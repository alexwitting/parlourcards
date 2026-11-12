import kotlinx.kover.gradle.plugin.dsl.KoverProjectExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.configure

/**
 * Coverage exclusions every module shares, so the numbers measure hand-written logic:
 * - Hilt's generated classes (`_Factory`, `_HiltModules`, `_MembersInjector`, `Hilt_*`) and
 *   `@Module` classes, which are wiring with no branches.
 * - Room's generated `*_Impl` classes, which only run against a real database.
 * - Compose UI, Activities and the Application class, which smoke and screenshot tests cover.
 *   These go by file name (`*ScreenKt*`, `*PreviewsKt*`, `*OverlaysKt*` …) more than by the
 *   `@Composable` annotation filter, which misses lambda bodies and a file's top-level constants.
 *   A file that's all Composable UI needs a name pattern here; split testable logic out of it
 *   (as `GinResultText.kt` is) rather than widening a pattern.
 */
class KoverConventionPlugin : Plugin<Project> {
    override fun apply(target: Project) {
        with(target) {
            pluginManager.apply("org.jetbrains.kotlinx.kover")

            extensions.configure<KoverProjectExtension> {
                reports {
                    filters {
                        excludes {
                            packages("dagger.hilt.internal.aggregatedroot.codegen", "hilt_aggregated_deps")
                            classes(
                                "*Factory",
                                "*Factory\$*",
                                "*_MembersInjector",
                                "*_HiltModules*",
                                "*.Hilt_*",
                                "*_GeneratedInjector",
                                "*_Impl",
                                "*_Impl\$*",
                                "*ScreenKt*",
                                "*PreviewsKt*",
                                "*OverlaysKt*",
                                "*NavGraphKt*",
                                "*ResultChromeKt*",
                                "*BoardRowsKt*",
                                "*Activity*",
                                "*Application*",
                                "*Module",
                                "*Module\$*",
                                // Add app-specific look-alikes here — e.g. a Room database class,
                                // or private UI-display data holders co-located inside Compose
                                // Screen files that map a domain enum to icon/title/copy text.
                            )
                            annotatedBy("androidx.compose.runtime.Composable")
                        }
                    }
                }
            }
        }
    }
}
