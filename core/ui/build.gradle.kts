plugins {
    id("cardgames.android.library")
    id("cardgames.compose")
    id("cardgames.detekt")
    // Screenshot baselines for the design-system catalogue (DesignSystemCatalog.kt).
    id("cardgames.robolectric")
}

android {
    namespace = "com.cardgames.core.ui"

    // The opt-in theme pack render (ThemePackRenderTest): -PrenderThemePacksTo=<assets/themes dir>.
    testOptions.unitTests.all { test ->
        providers.gradleProperty("renderThemePacksTo").orNull?.let { out ->
            test.systemProperty("cardgames.renderThemePacksTo", out)
            test.outputs.upToDateWhen { false }
        }
    }
}

dependencies {
    implementation(project(":core:domain"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.activity.compose)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testImplementation(libs.mockk)
    testRuntimeOnly(libs.junit5.engine)
}
