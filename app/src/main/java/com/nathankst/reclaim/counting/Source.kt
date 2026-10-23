package com.nathankst.reclaim.counting

enum class Source {
    INSTAGRAM_REELS,
    YOUTUBE_SHORTS;

    companion object {
        fun fromPackage(packageName: String?): Source? = when (packageName) {
            CountingConfig.INSTAGRAM_PACKAGE -> INSTAGRAM_REELS
            CountingConfig.YOUTUBE_PACKAGE -> YOUTUBE_SHORTS
            else -> null
        }
    }
}
