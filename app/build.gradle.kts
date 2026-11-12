plugins {
    id("cardgames.android.application")
    id("cardgames.compose")
    id("cardgames.hilt")
    id("cardgames.detekt")
    id("cardgames.kover")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.baselineprofile)
}

android {
    namespace = "com.cardgames"
    defaultConfig {
        applicationId = "com.cardgames"
        versionCode = 1
        versionName = "1.0.0"
    }
    buildTypes {
        // Installs beside the release build, with its own name and icon (src/debug/res).
        debug {
            applicationIdSuffix = ".dev"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            // Signed with the debug key so `installRelease` can put the minified build on a device
            // for testing. Replace with a real signing config before publishing.
            signingConfig = signingConfigs.getByName("debug")
        }
    }
    lint {
        // local.properties is IDE-generated, gitignored, and never touched by hand — its
        // Windows-style sdk.dir path trips this check on every Windows machine's default setup.
        disable += "PropertyEscape"
        // Lint suggests merging mipmap-anydpi-v26/ into an unqualified mipmap-anydpi/ (minSdk is
        // 26), but AAPT2 doesn't take mipmap-anydpi as an adaptive-icon bucket without the API
        // qualifier, so merging breaks resource linking.
        disable += "ObsoleteSdkInt"
    }
}

dependencies {
    // Installs the Baseline Profile (src/release/generated/baselineProfiles) when the app is installed.
    implementation(libs.androidx.profileinstaller)
    "baselineProfile"(project(":baselineprofile"))
    implementation(project(":core:domain"))
    implementation(project(":core:data"))
    implementation(project(":core:ui"))
    implementation(project(":feature:main"))
    implementation(project(":feature:solitaire"))
    implementation(project(":feature:ginrummy"))
    implementation(project(":feature:stats"))

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.core.splashscreen)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.hilt.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)
}

// The Baseline Profile plugin's builds (nonMinifiedRelease, benchmarkRelease) install as their own app,
// com.cardgames.benchmark, so profiling and benchmarks — which clear the app's data and uninstall it
// afterwards — never touch the real Parlour Cards. Set on the variants: the plugin creates those build
// types itself, after anything set on them in `android { }`.
androidComponents {
    onVariants { variant ->
        if (variant.buildType == "nonMinifiedRelease" || variant.buildType == "benchmarkRelease") {
            variant.applicationId.set("com.cardgames.benchmark")
        }
    }
}
