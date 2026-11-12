plugins {
    id("cardgames.android.library")
    id("cardgames.hilt")
    id("cardgames.detekt")
    id("cardgames.kover")
    alias(libs.plugins.kotlin.serialization)
    alias(libs.plugins.room)
}

android {
    namespace = "com.cardgames.core.data"
}

// Robolectric (the upgrade tests' real SQLite) and the exported schemas, which MigrationTestHelper
// reads as test assets to build each old database version. Through the current DSL type: AGP 9's
// `android { sourceSets }` accessor is the old API and doesn't cast.
extensions.configure<com.android.build.api.dsl.LibraryExtension> {
    testOptions.unitTests.isIncludeAndroidResources = true
    // The opt-in fixture writer (UpgradeFixtureWriter): -PwriteUpgradeFixture=<version>, run once per
    // release, writes that release's saves, stats and settings into src/test/resources/upgrade/.
    testOptions.unitTests.all { test ->
        providers.gradleProperty("writeUpgradeFixture").orNull?.let { version ->
            test.systemProperty("cardgames.writeUpgradeFixture", "$projectDir/src/test/resources/upgrade/$version")
            test.outputs.upToDateWhen { false }
        }
    }
    sourceSets
        .getByName("test")
        .assets.directories
        .add("$projectDir/schemas")
}

// Every database version's schema, committed: migrations are tested against the real old shapes.
room {
    schemaDirectory("$projectDir/schemas")
}

dependencies {
    implementation(project(":core:domain"))

    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.kotlinx.serialization.json)

    // --- Persistence (Room + DataStore) ---
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore.preferences)
    implementation(libs.security.crypto)

    // --- Uncomment as needed; add the matching [versions]/[libraries] entries back to
    // gradle/libs.versions.toml first (they're commented out there by default). ---
    // implementation(libs.workmanager.ktx)
    // implementation(libs.hilt.work)
    // implementation(libs.retrofit)
    // implementation(libs.okhttp)

    testImplementation(project(":core:testing"))
    testImplementation(libs.junit5.api)
    testRuntimeOnly(libs.junit5.engine)
    testImplementation(libs.mockk)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.turbine)

    // Upgrade tests: real SQLite under Robolectric (JUnit4, via the vintage engine) and Room's
    // MigrationTestHelper.
    testImplementation(libs.robolectric)
    testImplementation(libs.junit)
    testImplementation(libs.androidx.junit)
    testImplementation(libs.androidx.test.core)
    testImplementation(libs.room.testing)
    testRuntimeOnly(libs.junit5.vintage.engine)
}

// Regression gate, not a target — see :core:domain's build.gradle.kts for why.
kover {
    reports {
        verify {
            rule { minBound(90) }
        }
    }
}
