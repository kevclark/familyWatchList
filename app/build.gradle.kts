import java.util.Properties

// AGP 9.0+ built-in Kotlin support means org.jetbrains.kotlin.android is no longer applied
// here (https://kotl.in/gradle/agp-built-in-kotlin).
plugins {
    alias(libs.plugins.android.application)
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
    compileSdk = 37

    defaultConfig {
        applicationId = "org.seg7.familywatchlist"
        minSdk = 26
        targetSdk = 37
        versionCode = 4
        // SemVer (Kev, 2026-08-22): bump the alpha counter (alpha.2, alpha.3, ...) each time a
        // build goes out to his phone, so Settings' version row actually reflects "this changed"
        // rather than sitting static. versionCode stays a plain incrementing int per Play Store
        // convention; versionName is the human-facing SemVer string shown in Settings.
        versionName = "0.1.0-alpha.4"

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

    testOptions {
        unitTests {
            isIncludeAndroidResources = true
            all { it.systemProperty("robolectric.logging", "stdout") }
        }
    }

    // M6 fix: AppDatabaseMigrationTest's MigrationTestHelper loads exported schema JSON from
    // "assets" at runtime (see the Room migration-testing docs). Robolectric's unit tests read
    // assets from the *debug variant's own* merged assets (test_config.properties'
    // `android_merged_assets` always points at `mergeDebugAssets`, regardless of a `test`
    // sourceSet), not a separate unit-test asset set — so the schemas have to live under the
    // `debug` sourceSet specifically, not `test`, to be visible at all. Debug-only means this
    // never ships in a release build.
    sourceSets {
        getByName("debug") {
            assets.directories.add("$projectDir/schemas")
        }
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

// Room schema export (PLAN.md M1: "exported schemas checked in").
ksp {
    arg("room.schemaLocation", "$projectDir/schemas")
    arg("room.generateKotlin", "true")
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.navigation.compose)

    // Onboarding-complete flag + active-profile selection (M2a, PLAN.md §1/§5)
    implementation(libs.androidx.datastore.preferences)

    // Weekly shortlist regeneration + notification (PLAN.md §4, M3)
    implementation(libs.androidx.work.runtime.ktx)

    implementation(libs.kotlinx.serialization.json)
    implementation(libs.kotlinx.coroutines.core)

    // Poster/backdrop imagery with disk+memory caching and crossfade (PLAN.md §1, §5a motion).
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)

    // Room (M1)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // TMDB network client (M1)
    implementation(libs.retrofit)
    implementation(libs.retrofit.kotlinx.serialization)
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging.interceptor)

    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    testImplementation(libs.robolectric)
    testImplementation(libs.androidx.junit)
    // M6 fix: AppDatabaseMigrationTest drives real MIGRATION_7_8 SQL against exported schema
    // JSON via MigrationTestHelper (Robolectric provides real SQLite, so this runs on the JVM).
    testImplementation(libs.androidx.room.testing)
    // M3f: RecommendationSchedulerRescheduleTest drives real WorkManager under Robolectric to
    // assert on ExistingPeriodicWorkPolicy.KEEP vs UPDATE behaviour, not just delay-math.
    testImplementation(libs.androidx.work.testing)
    // Compose UI test for the log-watch flow, run on the JVM via Robolectric (PLAN.md §7
    // testing bar) so it lands in `./gradlew test` rather than needing a booted emulator.
    testImplementation(libs.androidx.compose.ui.test.junit4)
    testImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
}
