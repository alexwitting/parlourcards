plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
    alias(libs.plugins.android.test) apply false
    alias(libs.plugins.baselineprofile) apply false
    alias(libs.plugins.kotlin.jvm) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
    alias(libs.plugins.room) apply false
    alias(libs.plugins.hilt) apply false
    alias(libs.plugins.detekt) apply false
    alias(libs.plugins.ktlint) apply false
    alias(libs.plugins.kover) apply false
    alias(libs.plugins.roborazzi) apply false
    id("cardgames.kover")
}

// Every measured module, for Kover's merged report — add new modules here. Left out: :core:testing
// (test fakes only) and :core:ui (all Composable, so nothing is left to measure after the
// convention plugin's exclusions).
dependencies {
    kover(project(":app"))
    kover(project(":core:data"))
    kover(project(":core:domain"))
    kover(project(":feature:main"))
    kover(project(":feature:solitaire"))
    kover(project(":feature:ginrummy"))
    kover(project(":feature:stats"))
}

// Merged branch coverage, as a regression gate (each module's own gate checks line coverage —
// see core/domain/build.gradle.kts). Zero until there's a measured baseline to protect.
kover {
    reports {
        verify {
            rule {
                bound {
                    minValue = 0
                    coverageUnits = kotlinx.kover.gradle.plugin.dsl.CoverageUnit.BRANCH
                }
            }
        }
    }
}
