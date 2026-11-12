plugins {
    id("cardgames.kotlin.jvm")
    id("cardgames.detekt")
    id("cardgames.kover")
    alias(libs.plugins.kotlin.serialization)
}

dependencies {
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testImplementation(libs.junit5.params)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
    testImplementation(libs.mockk)
}

// Regression gate, not a target: just below the measured line coverage, so an accidental drop
// fails the build without forcing coverage-chasing.
kover {
    reports {
        verify {
            rule { minBound(92) }
        }
    }
}
