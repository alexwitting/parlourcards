import com.android.build.api.dsl.LibraryExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

class AndroidLibraryConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("com.android.library")

            extensions.configure<LibraryExtension> {
                compileSdk = 36
                defaultConfig {
                    minSdk = 26
                    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
                }
                compileOptions {
                    sourceCompatibility = JavaVersion.VERSION_21
                    targetCompatibility = JavaVersion.VERSION_21
                }
                // Test sources are covered by ktlint + detekt. Lint's unit-test analysis
                // (lintAnalyze*UnitTest) fails reading release KSP output that isn't generated yet when
                // lint runs alongside other tasks.
                lint { ignoreTestSources = true }
                // JVM unit tests run against a stub android.jar whose methods throw; return defaults
                // instead, so a ViewModel's Log.e on an error path needs no mocking. (Robolectric
                // tests use a real Android runtime and aren't affected.)
                testOptions.unitTests.isReturnDefaultValues = true
            }

            configureKotlinJvmAndTests()
        }
    }
}
