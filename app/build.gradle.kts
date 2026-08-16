import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.ksp)
}

// TMDB v4 read access token lives in the git-ignored local.properties (PLAN.md §3).
// It is injected into BuildConfig and must never be logged, printed, or committed.
val tmdbAccessToken: String = run {
    val propsFile = rootProject.file("local.properties")
    val props = Properties()
    if (propsFile.exists()) {
        propsFile.inputStream().use(props::load)
    }
    (props.getProperty("TMDB_ACCESS_TOKEN") ?: System.getenv("TMDB_ACCESS_TOKEN") ?: "").trim()
}

if (tmdbAccessToken.isEmpty()) {
    logger.warn(
        "WARNING: TMDB_ACCESS_TOKEN is not set in local.properties — TMDB calls will fail at runtime."
    )
}

android {
    namespace = "org.seg7.familywatchlist"
    compileSdk = 35

    defaultConfig {
        applicationId = "org.seg7.familywatchlist"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables { useSupportLibrary = true }

        // Escaped so a token containing quotes/backslashes can never break the generated source.
        val escapedToken = tmdbAccessToken.replace("\\", "\\\\").replace("\"", "\\\"")
        buildConfigField("String", "TMDB_ACCESS_TOKEN", "\"$escapedToken\"")
        buildConfigField("String", "TMDB_BASE_URL", "\"https://api.themoviedb.org/3/\"")
        buildConfigField("String", "TMDB_WATCH_REGION", "\"GB\"")
    }

    buildTypes {
        debug {
            isMinifyEnabled = false
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlin {
        compilerOptions {
            jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        }
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

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    implementation(libs.kotlinx.serialization.json)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
