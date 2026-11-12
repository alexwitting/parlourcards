import com.android.build.api.dsl.LibraryExtension
import io.github.takahirom.roborazzi.RoborazziExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.VersionCatalogsExtension
import org.gradle.kotlin.dsl.configure
import org.gradle.kotlin.dsl.dependencies
import org.gradle.kotlin.dsl.getByType

/**
 * Robolectric and Compose UI tests, for modules with screens to test (opt-in, like
 * `cardgames.compose` and `cardgames.hilt`).
 *
 * Compose's test rule is JUnit4, and every test task here runs on the JUnit Platform, so the
 * JUnit vintage engine runs the JUnit4 tests alongside the JUnit5 ones.
 *
 * Also sets up Roborazzi: baselines live in each module's `src/test/screenshots`;
 * `recordRoborazziDebug` re-records them and `verifyRoborazziDebug` fails on any pixel change. A
 * plain `test` run still runs the screenshot tests, without recording or comparing.
 */
class RobolectricConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("io.github.takahirom.roborazzi")
            pluginManager.withPlugin("com.android.library") {
                extensions.configure<LibraryExtension> {
                    testOptions.unitTests.isIncludeAndroidResources = true
                    testOptions.unitTests.all { test ->
                        // Hardware-accelerated pixel copy so shadows and blending render as on a device.
                        test.systemProperty("robolectric.pixelCopyRenderMode", "hardware")
                        // Robolectric extracts its native graphics library into the temp dir; modules
                        // testing in parallel would race on the shared one ("Unable to load Robolectric
                        // native runtime library"), so each module's tests get their own.
                        val tmp = layout.buildDirectory.dir("tmp/robolectric").get().asFile
                        test.systemProperty("java.io.tmpdir", tmp.absolutePath)
                        test.doFirst { tmp.mkdirs() }
                    }
                }
            }
            extensions.configure<RoborazziExtension> {
                outputDir.set(layout.projectDirectory.dir("src/test/screenshots"))
            }

            val libs = extensions.getByType<VersionCatalogsExtension>().named("libs")
            dependencies {
                "testImplementation"(libs.findLibrary("robolectric").get())
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-junit4").get())
                "testImplementation"(libs.findLibrary("androidx-compose-ui-test-manifest").get())
                "testImplementation"(libs.findLibrary("androidx-junit").get())
                "testImplementation"(libs.findLibrary("junit").get())
                "testImplementation"(libs.findLibrary("roborazzi").get())
                "testImplementation"(libs.findLibrary("roborazzi-compose").get())
                "testImplementation"(libs.findLibrary("roborazzi-junit-rule").get())
                "testRuntimeOnly"(libs.findLibrary("junit5-vintage-engine").get())
            }
        }
    }
}
