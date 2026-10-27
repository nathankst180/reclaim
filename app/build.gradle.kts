plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.compose.compiler)
}

android {
    namespace = "com.nathankst.reclaim"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.nathankst.reclaim"
        minSdk = 28
        targetSdk = 36
        versionCode = 7
        versionName = "0.6.0"
    }

    androidResources {
        localeFilters += "en"
    }

    buildTypes {
        debug {
            buildConfigField("boolean", "INSPECTOR", "true")
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName("debug") // only for personal testing
            buildConfigField("boolean", "INSPECTOR", "false")
        }
        // Same speed as release, but with the debug screen and test counter. Use this to test accuracy.
        create("inspect") {
            initWith(getByName("release"))
            matchingFallbacks += "release"
            versionNameSuffix = "-inspect"
            buildConfigField("boolean", "INSPECTOR", "true")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.kotlinx.coroutines.android)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
}
