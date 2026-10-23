# Reclaim — Reels & Shorts Counter (Final Build Plan v3)

> **For Claude Code:** Read this whole file before writing any code. Build in the order in section 9, in **3 stops**. At each stop, tell me what to test on my phone. Do not skip ahead.
>
> Working name: **Reclaim**. Package: `com.nathankst.reclaim`.
> Goal: a very lightweight Android app that **accurately counts every Instagram Reel and YouTube Short the user moves to**. It shows a tiny counter in the corner while they watch, plus simple daily and weekly stats. The tone is awareness, not punishment.
>
> **Scope for v1: ONLY the Instagram Reels player and the YouTube Shorts player.** Not the normal Instagram feed, not normal YouTube videos, no other apps.
> **Accuracy is the #1 priority.** A wrong count makes the app useless.

---

## 0. Decisions already made

| Topic | Decision |
|---|---|
| Platform | Android only, native |
| Language / UI | Kotlin + Jetpack Compose + Material 3 |
| Theme | **Dark only** for v1 (the "A+B Mix · Dark" design, section 7) |
| What counts as +1 | Each time the player moves to a **different** reel or short |
| Swipe back up to an old reel | **Counts** (setting `countBackward = true`, can be switched off) |
| Half-swipe that bounces back | **0** |
| Corner counter | Today's total (Reels + Shorts), "glass pill" style, top-right |
| Daily limit | Default 300, gentle notification once a day, **no blocking** |
| Data | 100% on the phone. **No INTERNET permission.** |
| Size target | Release APK **under 3 MB** |
| Purpose of v1 | Awareness only. No blocking, no pause screen. |
| Distribution | Personal use first; **Play Store later** (so keep the privacy disclosure, a real privacy policy and the Accessibility declaration in mind) |
| Test phone | Android 14 or newer, USB debugging to this PC: Claude installs the APK and reads Logcat itself |
| Apps in v1 | Official `com.instagram.android` (Reels) and `com.google.android.youtube` (Shorts) only. More apps later by adding IDs to `CountingConfig.kt` |
| Design source | Section 7 of this file only (no Figma or mockups) |
| Pace | 3 stops (section 9) |

---

## 1. Tech stack (use the latest stable versions at build time)

- **Kotlin** 2.x (latest stable), with K2 compiler and the Compose compiler Gradle plugin
- **Gradle Kotlin DSL** + version catalog `gradle/libs.versions.toml`
- **Android Gradle Plugin**: latest stable
- **Jetpack Compose**: latest Compose BOM, Material 3
- **Navigation Compose**: 3 routes (onboarding, home, settings) + debug route
- **Room** with KSP (not kapt) for daily stats
- **DataStore Preferences** for settings
- **Kotlin Coroutines + Flow**
- **Lifecycle ViewModel Compose**
- **JUnit 4 + kotlinx-coroutines-test** for unit tests
- `minSdk = 28` (needed for `scrollDeltaY`), `targetSdk` and `compileSdk` = latest stable
- Fonts: **Inter** (text) and **JetBrains Mono** (numbers), bundled as `res/font` files (only the weights used: Inter 400/500/600, JetBrains Mono 700). Do NOT use Downloadable Fonts (needs network/Play Services).

**Do NOT add:** Firebase, Retrofit/OkHttp, Ktor, Lottie, Coil/Glide, any analytics or crash SDK, Accompanist, Hilt/Koin/Dagger, any chart library, any `material-icons-extended` (too big; draw the few icons as vector drawables or `ImageVector`).

**Release build:**
```kotlin
buildTypes {
    release {
        isMinifyEnabled = true
        isShrinkResources = true
        proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        signingConfig = signingConfigs.getByName("debug") // only for personal testing
    }
}
```
Keep `resourceConfigurations += listOf("en")` to drop unused language resources.

---

## 2. Permissions (AndroidManifest)

- `BIND_ACCESSIBILITY_SERVICE` (on the service only)
- `POST_NOTIFICATIONS` (Android 13+, for the limit alert, optional)
- `RECEIVE_BOOT_COMPLETED` is **not** needed (the accessibility service restarts by itself)
- **No `INTERNET`. No `SYSTEM_ALERT_WINDOW`. No `PACKAGE_USAGE_STATS`.**

Add a check in the build or a unit test that the merged manifest has no INTERNET permission.

---

## 3. Architecture

```
Instagram / YouTube
      │  accessibility events (only from these 2 packages)
      ▼
ScrollAccessibilityService   (thin: converts events → small signals)
      │
      ├─► PlayerDetector     "are we inside the Reels / Shorts player?"
      │
      ├─► ReelCounter        pure Kotlin: "did the visible video change?" → +1
      │
      ├─► SessionTimer       time spent inside the player
      │
      ├─► CounterOverlay     glass pill in the corner
      │
      └─► LiveStats          in-memory today counts (StateFlow)
                │   flush every 30 s / on leaving player / on screen off
                ▼
           Room (daily_stats)  ──►  HomeViewModel  ──►  Compose UI
```

Rules:
- The service does **no heavy work** on the main thread. Node lookups are short and throttled.
- `ReelCounter` has **no Android imports** so it can be fully unit-tested.
- One `AppContainer` (created in `ReclaimApp`) holds the database, repositories, settings and `LiveStats`. The service and the UI both get them from there.

---

## 4. Accessibility service

### 4.1 Config — `res/xml/accessibility_config.xml`
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewScrolled|typeWindowStateChanged|typeWindowContentChanged"
    android:packageNames="com.instagram.android,com.google.android.youtube"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="50"
    android:description="@string/accessibility_description" />
```
- `packageNames` limits events to these 2 apps → almost no battery use.
- `canRetrieveWindowContent` is needed **only** to read view IDs (resource names) to know we are in the Reels/Shorts player.
- **Never read or store text, captions, usernames, comments or any content.** Never call `.text` or `.contentDescription` on nodes, except inside the debug inspector, and even there do not log them.

`accessibility_description` string (shown in Android settings):
> "Reclaim counts how many Instagram Reels and YouTube Shorts you watch. It only checks which screen is open and when the video changes. It does not read your messages, captions or any text, and nothing ever leaves your phone."

### 4.2 Service lifecycle
- `onServiceConnected`: get `AppContainer`, create overlay, register a `BroadcastReceiver` for `ACTION_SCREEN_OFF` and `ACTION_SCREEN_ON`, start the 30 s flush coroutine (`serviceScope` with `SupervisorJob + Dispatchers.Default`).
- `onAccessibilityEvent`: map the event into a `ScrollSignal` / player-state update (section 5). Must be fast.
- `onInterrupt`: nothing special.
- `onDestroy` / `onUnbind`: flush counts, remove overlay, cancel scope, unregister receiver.
- Expose a `StateFlow<Boolean> isRunning` in `AppContainer` so the UI can show "Tracking is ON/OFF". Also check with `AccessibilityManager.getEnabledAccessibilityServiceList` when the app opens.

---

## 5. Accurate counting (the most important part)

**Rule: +1 each time the Reels/Shorts player shows a different video than before.**
Do NOT count raw scroll events (one swipe sends many). Do NOT count by time alone.

### 5.1 Step A — `PlayerDetector`: are we in the player?
- Both players use a vertical pager (ViewPager2 / RecyclerView). We find it by **view ID**.
- All IDs live in **`CountingConfig.kt`** as lists, because the apps change them in updates:
```kotlin
object CountingConfig {
    val INSTAGRAM_PLAYER_IDS = listOf(
        "com.instagram.android:id/clips_viewer_view_pager",
        "com.instagram.android:id/clips_viewer_pager",
    )
    val YOUTUBE_PLAYER_IDS = listOf(
        "com.google.android.youtube:id/reel_recycler",
        "com.google.android.youtube:id/reel_player_page_container",
    )
    // ⚠ These are starting guesses. Phase 2 confirms the real IDs on the phone.

    const val COUNT_BACKWARD_DEFAULT = true
    const val MIN_GAP_BETWEEN_COUNTS_MS = 250L   // ignore duplicate events
    const val GESTURE_END_MS = 300L              // fallback: no event for 300 ms = gesture ended
    const val FALLBACK_MIN_SCREEN_FRACTION = 0.5f // fallback: must move ≥ 50% of screen
    const val PLAYER_CHECK_THROTTLE_MS = 500L
}
```
- Check on every `TYPE_WINDOW_STATE_CHANGED`, and on `TYPE_WINDOW_CONTENT_CHANGED` at most once per 500 ms.
- Use `rootInActiveWindow?.findAccessibilityNodeInfosByViewId(id)`; the player is "visible" if any matching node `isVisibleToUser`.
- Also accept a `TYPE_VIEW_SCROLLED` event whose **source view ID** is one of the player IDs as proof we are in the player (cheap, no tree search).
- Recycle nodes on API < 33.
- Output: `PlayerState(inPlayer: Boolean, source: Source?)` where `Source = INSTAGRAM_REELS | YOUTUBE_SHORTS`.

### 5.2 Step B — `ReelCounter`: did the video change?
Input (from the service):
```kotlin
data class ScrollSignal(
    val timeMs: Long,
    val source: Source,
    val fromIndex: Int,     // -1 if unknown
    val toIndex: Int,       // -1 if unknown
    val itemCount: Int,     // -1 if unknown
    val deltaY: Int,        // scrollDeltaY (API 28+), 0 if unknown
    val screenHeightPx: Int,
)
```
Events from the service: `onEnterPlayer(source, time)`, `onLeavePlayer(time)`, `onScroll(signal)`, `onScreenOff()`, `onScreenOn()`.

**Primary method (index-based, most accurate):**
- On scroll from the pager: current position = `fromIndex` (for a full-page pager, `fromIndex == toIndex` = the visible page).
- If position ≠ `lastPosition` → new video:
  - forward (position > last) → `+1`
  - backward (position < last) → `+1` only if `countBackward`
  - then `lastPosition = position`
- Same position (bounce back, wobble) → `0`.

**Fallback method (only when index is -1 on that app version):**
- Add up `deltaY` for one gesture (events less than 300 ms apart).
- When no event arrives for 300 ms: if `|total| ≥ 50% of screenHeightPx` → `+1` (or `+N` where N = round(|total| / screenHeight), for multi-page flings). Less → `0`.
- Direction from the sign of the total (for `countBackward`).

**Guard rails:**
- Ignore all scroll signals while `inPlayer == false` (feed, comments sheet over the player, profile, search, normal videos).
- **Entering the player = +1** for the first video the user sees. Do not count that same video again when the pager reports its first position. (Set `lastPosition` from the first signal without counting.)
- Pager re-created (came back from background, rotated, comments closed) → if the position equals `lastPosition`, no count. If the app reset its index to 0 after coming back, treat the first signal after re-entering as "set position, no count" (only within 1 s of re-entering).
- Leaving the player for less than **3 s** and coming back (e.g. opened comments, notification shade) → **not** a new entry, no extra +1.
- Never count twice within `MIN_GAP_BETWEEN_COUNTS_MS`.
- Ignore everything while the screen is off.
- Output: `StateFlow<CounterResult>` plus a callback `onCounted(source, timeMs)`.

### 5.3 Step C — Unit tests (`ReelCounterTest.kt`)
Must pass before the overlay is built:
1. Enter player → 1
2. 20 forward page changes → 21 total (1 + 20)
3. Many scroll events with the same index (bounce) → no extra
4. 5 backward changes with `countBackward = true` → +5; with `false` → +0
5. Duplicate events within 250 ms → counted once
6. Leave and come back within 3 s at the same index → no extra
7. Leave for 10 s, come back → +1 (new entry)
8. Index reset to 0 right after re-entering → no extra
9. Fallback path: deltaY 60% of screen → +1; 30% → 0; 210% fling → +2
10. Screen off → signals ignored
11. Signals while `inPlayer = false` → ignored

### 5.4 Step D — Debug inspector (debug builds only)
- A screen reachable by long-pressing the app title on Home (only when `BuildConfig.DEBUG`).
- **Inspector log toggle:** logs to Logcat with tag `ReclaimDbg`: event type, package, source view ID, class name, fromIndex, toIndex, itemCount, scrollDeltaY, `inPlayer`, and the decision (`COUNTED +1` / `IGNORED: reason`). **No text content.**
- **"Dump view IDs" button:** when tapped, the next time Instagram or YouTube is in front, log all view IDs on screen (IDs only, no text). Used in phase 2 to find the real player IDs.
- **Test counter card:** big live number for the current test, separate from the real daily stats, with a **Reset** button. This makes accuracy testing easy: reset → do 20 swipes → check the number.
- Remove all of this from release builds (`if (BuildConfig.DEBUG)`).

---

## 6. Other features

### 6.1 Time spent (`SessionTimer`)
- Runs only while `inPlayer == true` and the screen is on.
- Pauses on leaving the player, app switch or screen off. Adds the time to `LiveStats`.

### 6.2 Distance
- Distance (cm) = counted videos × (screen height px ÷ `ydpi` × 2.54).
- Format: under 1000 m → "84 m"; otherwise "1.2 km".

### 6.3 Corner counter overlay (`CounterOverlay`)
- `WindowManager.addView` with `TYPE_ACCESSIBILITY_OVERLAY` from the service → no extra permission.
- Flags: `FLAG_NOT_TOUCHABLE or FLAG_NOT_FOCUSABLE or FLAG_LAYOUT_IN_SCREEN or FLAG_LAYOUT_NO_LIMITS`, format `TRANSLUCENT`.
- A plain `TextView` (not Compose).
- **Glass pill style:** background `#8C000000` (black 55%), rounded 999dp, padding 4dp × 9dp, white text 11sp, JetBrains Mono bold, text `↕ 247`.
- Position: top-right, `y` = status bar height + 8dp, `x` = 12dp margin. User can switch to top-left in Settings.
- Show only while `inPlayer == true`; hide right away on leave or screen off (`visibility = GONE`, don't remove/re-add each time).
- When the number changes: scale 1.0 → 1.15 → 1.0 in 150 ms.
- Settings toggle to turn it off.

### 6.4 Storage (Room)
```kotlin
@Entity(tableName = "daily_stats", primaryKeys = ["date", "source"])
data class DailyStatsEntity(
    val date: String,      // "yyyy-MM-dd", local time
    val source: String,    // "INSTAGRAM_REELS" | "YOUTUBE_SHORTS"
    val count: Int,
    val timeMs: Long,
)
```
DAO: `addTo(date, source, count, timeMs)` as an upsert that **adds** to the existing row; `observeDay(date)`; `observeRange(from, to)` for the 7-day chart; `deleteAll()`.
- `LiveStats` keeps "unsaved" counts in memory. **Flush** every 30 s, on leaving the player, on screen off and in `onDestroy`.
- UI and overlay show saved + unsaved, so the number is always live.
- Midnight: the date key changes by itself. When the date changes during a session, flush the old day first.

### 6.5 Settings (DataStore)
`dailyLimit` (default 300, 0 = off) · `overlayEnabled` (true) · `overlayCorner` (TOP_RIGHT) · `countBackward` (true) · `onboardingDone` (false) · `lastLimitAlertDate` ("")

### 6.6 Daily limit notification
- When today's total passes `dailyLimit` and `lastLimitAlertDate != today` → one notification:
  "You've watched 300 reels & shorts today. Maybe take a short break? 🌿"
- One low-importance notification channel "Daily limit".

---

## 7. UI — "A+B Mix · Dark" design

### 7.1 Design tokens (`ui/theme`)
| Token | Value |
|---|---|
| background | `#0E0F12` |
| card | `#17191D` |
| text | `#F1F2F4` |
| muted text | `#A1A6AE` |
| line / border | `#2A2D33` |
| track (empty) | `#2A2D33` |
| chart bar | `#30343B` |
| accent (mint) | `#7DD3A8` |
| warning (over limit) | `#F2B84B` |
| Card radius | 24dp (big cards), 20dp (small stat cards) |
| Screen padding | 24dp sides, 56dp top, gap 18dp |
| Fonts | Inter for text; **JetBrains Mono Bold** for all numbers |

Text styles: big number 80sp mono bold, letter spacing -3sp · stat value 20sp mono bold · title 20sp Inter SemiBold · body 14sp · label 12–13sp · small uppercase labels with 1.5sp letter spacing.
Use edge-to-edge (`enableEdgeToEdge()`) with dark status/navigation bars.

### 7.2 Home screen (top to bottom)
1. **Header:** small uppercase date ("THU · 22 OCT") in muted, "Today" title below. Right: 44dp round settings button with a border.
2. **Hero card:** "You swiped" (muted) → **big mono number in mint** → "reels & shorts today" → **10-segment bar** (filled segments = percent of limit, mint; turn amber `#F2B84B` when over the limit) → row "82% of 300 limit" / "53 left".
3. **2×2 stat grid:** Time spent · Distance · Instagram Reels · YouTube Shorts (label muted, value mono).
4. **Last 7 days card:** uppercase label + "12% less" (or "8% more") on the right in mint, comparing to the 7 days before. 7 bars drawn with simple `Box`es (height in proportion to the max), today in mint, others `#30343B`, day letters below.
5. If the service is off: a soft card at the top "Tracking is paused · Turn on" that opens accessibility settings.
6. Empty state (count 0): number shows "0" and the text "Nothing yet today. Nice."

### 7.3 Onboarding (first launch)
Same dark style, 3 pages, a "Continue" button at the bottom:
1. "See how many reels you really watch." Short text.
2. **Privacy disclosure (required by Google Play)**: what is read (only which screen is open and when the video changes, in Instagram and YouTube), what is NOT read (messages, captions, any text), nothing leaves the phone, no internet permission. Button "I understand".
3. "Turn on Reclaim" → opens `Settings.ACTION_ACCESSIBILITY_SETTINGS`. Live status chip (OFF / ON) that updates in `onResume`. Short help text: *"Can't turn it on? Settings → Apps → Reclaim → ⋮ → Allow restricted settings."* Also a hint for Xiaomi/Vivo/Oppo/Realme: set battery to "No restrictions". Then ask for notification permission (optional) → Home.

### 7.4 Settings
Daily limit (stepper or slider 50–1000, step 50, or Off) · Corner counter on/off · Corner (top-right / top-left) · Count swiping back (on/off) · Privacy note · "Delete all data" (confirm dialog) · App version.

---

## 8. Project structure
```
app/src/main/java/com/nathankst/reclaim/
  ReclaimApp.kt                  // Application + AppContainer
  AppContainer.kt
  MainActivity.kt                // edge-to-edge, NavHost
  service/
    ScrollAccessibilityService.kt
    PlayerDetector.kt
    CounterOverlay.kt
    SessionTimer.kt
    DebugInspector.kt            // debug only
  counting/
    CountingConfig.kt
    Source.kt
    ScrollSignal.kt
    ReelCounter.kt               // pure Kotlin
  data/
    db/AppDatabase.kt, DailyStatsDao.kt, DailyStatsEntity.kt
    prefs/SettingsRepository.kt
    LiveStats.kt
    StatsRepository.kt
  ui/
    theme/Color.kt, Type.kt, Theme.kt
    home/HomeScreen.kt, HomeViewModel.kt, WeekChart.kt, SegmentBar.kt
    onboarding/OnboardingScreen.kt
    settings/SettingsScreen.kt
    debug/DebugScreen.kt         // debug only
  util/Formatters.kt, DateUtils.kt, Notifier.kt
app/src/main/res/xml/accessibility_config.xml
app/src/main/res/font/ (inter_regular, inter_medium, inter_semibold, jetbrains_mono_bold)
app/src/test/java/com/nathankst/reclaim/counting/ReelCounterTest.kt
```

---

## 9. Build order — 3 stops

The phases below are still done in order. Stop and wait for me only here:
- **Stop 1:** after Phase 2 (project, service, debug inspector). I open Reels and Shorts while Claude reads Logcat over USB, then Claude fills in the real IDs.
- **Stop 2:** after Phase 4 (counter + unit tests + accuracy test passing).
- **Stop 3:** after Phase 9 (everything else, release check).

**Phase 1 — Setup**
New project, version catalog, dark theme + fonts, empty Home with the header. `./gradlew assembleDebug` works. → *I install it and see the app open.*

**Phase 2 — Service + debug inspector (NO counting yet)**
Accessibility service, config XML, debug screen with "Inspector log" and "Dump view IDs".
→ *I enable the service, open Reels and Shorts, and send you the Logcat output (`adb logcat -s ReclaimDbg`).*
→ You then update `CountingConfig.kt` with the **real** player IDs and say whether `fromIndex/toIndex` are filled (primary method) or -1 (fallback needed), for **each** app.

**Phase 3 — ReelCounter + unit tests**
Pure logic + all tests in 5.3 passing (`./gradlew test`). Connect it to the service and to the debug **test counter card**.
→ *I run the accuracy test (section 10) with the test counter.*

**Phase 4 — Fix accuracy**
Tune using my test results and logs. Repeat until section 10 passes.

**Phase 5 — Overlay** (glass pill, show/hide, pop animation)

**Phase 6 — Storage + time + distance** (Room, LiveStats, flush, SessionTimer)

**Phase 7 — Home screen** (section 7.2)

**Phase 8 — Onboarding + Settings + limit notification**

**Phase 9 — Polish + release check:** empty states, app icon (simple vector: mint hourglass or ↕ on dark), `./gradlew assembleRelease`, check APK size < 3 MB and no INTERNET permission (`aapt dump permissions` or check the merged manifest).

---

## 10. Accuracy test (must pass before Phase 5)

Use the debug test counter. **Reset before each test.** Do each test in **Instagram Reels** and **YouTube Shorts**.

| # | Test | Expected |
|---|---|---|
| 1 | Open the player (first video) | 1 |
| 2 | Then 20 normal swipes down | 21 |
| 3 | 20 very fast swipes | +20 |
| 4 | 10 slow swipes | +10 |
| 5 | 5 half-swipes that bounce back | +0 |
| 6 | 5 swipes back up | +5 (or +0 if setting off) |
| 7 | Open comments, scroll comments, close | +0 |
| 8 | Pull down the notification shade, close it | +0 |
| 9 | Go to home screen, come back within 3 s | +0 |
| 10 | Screen off, screen on, still in player | +0 |
| 11 | Scroll the normal Instagram feed / YouTube home | +0 |
| 12 | Open a reel from a DM or a profile grid, then swipe 5 | 1 + 5 |
| 13 | Sponsored reel / ad in between | counts like any video |
| 14 | Video ends and auto-plays the next (if the app does this) | +1 |

Target: **exact match** in normal use, **never more than ±1 in 50 swipes**.
Write down the results like: `IG test 3: expected 20, got 19` and send them with the Logcat output.

---

## 11. Install & test on the phone
1. Phone: Settings → About phone → tap **Build number** 7 times → Developer options → turn on **USB debugging**.
2. `./gradlew installDebug` (phone connected), or copy `app/build/outputs/apk/debug/app-debug.apk` to the phone and open it.
3. **Android 13+:** Settings → Apps → Reclaim → ⋮ (top right) → **Allow restricted settings**. Then Settings → Accessibility → Reclaim → ON.
4. Xiaomi / Vivo / Oppo / Realme: Battery → Reclaim → **No restrictions**, and lock Reclaim in recent apps.
5. Logs: `adb logcat -s ReclaimDbg`

---

## 12. Quality checklist (before daily use)
- [ ] All unit tests pass
- [ ] All accuracy tests pass in both apps
- [ ] Overlay never blocks taps; hidden outside the player and when the screen is off
- [ ] Counts survive app kill and phone restart
- [ ] New day starts at 0
- [ ] No INTERNET permission
- [ ] Release APK < 3 MB
- [ ] Battery ≤ ~1% per day (check after a full day)
- [ ] Works on Android 9 (minSdk 28) → latest; tested on Android 14+

---

## 13. Later (not now)
- Light theme, Play Store (Accessibility API declaration form, demo video, privacy policy page, AAB with a real signing key)
- More apps (Facebook Reels, Snapchat Spotlight, Moj, Josh) by adding IDs to `CountingConfig.kt`
- Weekly summary card, home-screen widget
