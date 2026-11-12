// The Baseline Profile generator and the macrobenchmarks: journeys through the app, run on a
// connected phone (`./gradlew :app:generateReleaseBaselineProfile`, `./gradlew
// :baselineprofile:connectedBenchmarkReleaseAndroidTest`). They drive the plugin's own builds of the
// app, installed as com.cardgames.benchmark, so the real app's saves are never touched.
plugins {
    alias(libs.plugins.android.test)
    alias(libs.plugins.baselineprofile)
    id("cardgames.detekt")
}

android {
    namespace = "com.cardgames.baselineprofile"
    compileSdk = 36
    defaultConfig {
        // Baseline Profile generation needs Android 9+; the app itself still runs from 8.
        minSdk = 28
        targetSdk = 35
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_21
        targetCompatibility = JavaVersion.VERSION_21
    }
    targetProjectPath = ":app"
}

baselineProfile {
    useConnectedDevices = true
}

dependencies {
    implementation(libs.androidx.junit)
    implementation(libs.uiautomator)
    implementation(libs.benchmark.macro.junit4)
}

// The app id of the build under test (com.cardgames.benchmark), passed to the journeys.
androidComponents {
    onVariants { variant ->
        val loader = variant.artifacts.getBuiltArtifactsLoader()
        variant.instrumentationRunnerArguments.put(
            "targetAppId",
            variant.testedApks.map { loader.load(it)?.applicationId ?: error("No tested app") },
        )
    }
}
