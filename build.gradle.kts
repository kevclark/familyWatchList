// Root build file — plugins are declared here (apply false) and applied in :app.
// AGP 9.0+ has built-in Kotlin support: org.jetbrains.kotlin.android is no longer applied
// (https://kotl.in/gradle/agp-built-in-kotlin). kotlin-compose/serialization/ksp are unaffected.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
    alias(libs.plugins.ksp) apply false
}
