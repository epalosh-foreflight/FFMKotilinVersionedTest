package patches.buildTypes

import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.triggers.VcsTrigger
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.ui.*

/*
This patch script was generated by TeamCity on settings change in UI.
To apply the patch, change the buildType with id = 'Release'
accordingly, and delete the patch script.
*/
changeBuildType(RelativeId("Release")) {
    triggers {
        val trigger1 = find<VcsTrigger> {
            vcs {
                enabled = false
                branchFilter = """
                    +:release/*
                    +:firstrun/*
                """.trimIndent()

                buildParams {
                    param("env.ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS", "false")
                }
            }
        }
        trigger1.apply {
            clearBuildParams()
            buildParams {
                checkbox("env.ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS", "false", label = "Enable Developmental Feature Flags", description = "Specifies the ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS to override default behavior of Development phase feature flags excluded from Release builds", display = ParameterDisplay.PROMPT,
                          checked = "true", unchecked = "false")
            }
        }
        val trigger2 = find<VcsTrigger> {
            vcs {
                enabled = false
                branchFilter = "+:develop"

                buildParams {
                    param("env.ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS", "false")
                }
            }
        }
        trigger2.apply {
            clearBuildParams()
            buildParams {
                checkbox("env.ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS", "false", label = "Enable Developmental Feature Flags", description = "Specifies the ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS to override default behavior of Development phase feature flags excluded from Release builds", display = ParameterDisplay.PROMPT,
                          checked = "true", unchecked = "false")
            }
        }
    }

    expectDisabledSettings("RQ_378", "RQ_384", "RUNNER_12", "RUNNER_13", "RUNNER_15", "RUNNER_232")
    updateDisabledSettings("RQ_378", "RQ_384", "RUNNER_12", "RUNNER_13", "RUNNER_15", "RUNNER_232", "TRIGGER_3", "TRIGGER_5")
}
