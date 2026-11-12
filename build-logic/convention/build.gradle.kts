plugins {
    `kotlin-dsl`
}

group = "cardgames.buildlogic"

java {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
}

dependencies {
    compileOnly(libs.android.gradlePlugin)
    compileOnly(libs.kotlin.gradlePlugin)
    compileOnly(libs.ksp.gradlePlugin)
    compileOnly(libs.hilt.gradlePlugin)
    compileOnly(libs.detekt.gradlePlugin)
    compileOnly(libs.ktlint.gradlePlugin)
    compileOnly(libs.kover.gradlePlugin)
    compileOnly(libs.roborazzi.gradlePlugin)
}

gradlePlugin {
    plugins {
        register("kotlinJvm") {
            id = "cardgames.kotlin.jvm"
            implementationClass = "KotlinJvmConventionPlugin"
        }
        register("androidLibrary") {
            id = "cardgames.android.library"
            implementationClass = "AndroidLibraryConventionPlugin"
        }
        register("androidApplication") {
            id = "cardgames.android.application"
            implementationClass = "AndroidApplicationConventionPlugin"
        }
        register("hilt") {
            id = "cardgames.hilt"
            implementationClass = "HiltConventionPlugin"
        }
        register("compose") {
            id = "cardgames.compose"
            implementationClass = "ComposeConventionPlugin"
        }
        register("detekt") {
            id = "cardgames.detekt"
            implementationClass = "DetektConventionPlugin"
        }
        register("kover") {
            id = "cardgames.kover"
            implementationClass = "KoverConventionPlugin"
        }
        register("robolectric") {
            id = "cardgames.robolectric"
            implementationClass = "RobolectricConventionPlugin"
        }
    }
}
