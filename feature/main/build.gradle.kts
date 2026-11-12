plugins {
    id("cardgames.android.library")
    id("cardgames.compose")
    id("cardgames.hilt")
    id("cardgames.detekt")
    id("cardgames.kover")
    id("cardgames.robolectric")
}

android {
    namespace = "com.cardgames.feature.main"
}

dependencies {
    implementation(project(":core:domain"))
    implementation(project(":core:ui"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)

    testImplementation(project(":core:testing"))
    testImplementation(project(":core:testing-ui"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

// Regression gate, not a target — see :core:domain's build.gradle.kts for why.
kover {
    reports {
        verify {
            rule { minBound(94) }
        }
    }
}
