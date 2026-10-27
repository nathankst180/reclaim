plugins {
    id("com.android.application")
}

android {
    namespace = "dev.reclaim.fakereels"
    compileSdk = 36

    defaultConfig {
        // Same package as Instagram (or YouTube, with -PfakePackage=com.google.android.youtube) on purpose,
        // so Reclaim listens to it. Emulator only.
        applicationId = providers.gradleProperty("fakePackage").getOrElse("com.instagram.android")
        minSdk = 28
        targetSdk = 36
        // The real YouTube on the emulator has a huge version code, so the stand-in must be higher.
        versionCode = providers.gradleProperty("fakeVersionCode").map { it.toInt() }.getOrElse(1)
        versionName = "0.0-fake"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation("androidx.recyclerview:recyclerview:1.4.0")
    implementation("androidx.viewpager2:viewpager2:1.1.0")
}
