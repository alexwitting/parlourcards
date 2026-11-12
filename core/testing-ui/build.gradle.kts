// Shared helpers for the Robolectric/Compose tests of every screen module: screenshots, the
// device sizes tests render at, and a recording GameFeedback. testImplementation only — the
// Android-side companion of the plain-JVM :core:testing.
plugins {
    id("cardgames.android.library")
    id("cardgames.compose")
    id("cardgames.detekt")
}

android {
    namespace = "com.cardgames.core.testing.ui"
}

dependencies {
    api(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    api(libs.androidx.compose.ui.test.junit4)
    implementation(libs.roborazzi)
    implementation(libs.roborazzi.compose)
    implementation(libs.robolectric)
}
