import jetbrains.buildServer.configs.kotlin.*
import jetbrains.buildServer.configs.kotlin.buildFeatures.PullRequests
import jetbrains.buildServer.configs.kotlin.buildFeatures.commitStatusPublisher
import jetbrains.buildServer.configs.kotlin.buildFeatures.perfmon
import jetbrains.buildServer.configs.kotlin.buildFeatures.pullRequests
import jetbrains.buildServer.configs.kotlin.buildSteps.exec
import jetbrains.buildServer.configs.kotlin.buildSteps.script
import jetbrains.buildServer.configs.kotlin.buildSteps.xcode
import jetbrains.buildServer.configs.kotlin.triggers.retryBuild
import jetbrains.buildServer.configs.kotlin.triggers.vcs
import jetbrains.buildServer.configs.kotlin.vcs.GitVcsRoot

/*
The settings script is an entry point for defining a TeamCity
project hierarchy. The script should contain a single call to the
project() function with a Project instance or an init function as
an argument.

VcsRoots, BuildTypes, Templates, and subprojects can be
registered inside the project using the vcsRoot(), buildType(),
template(), and subProject() methods respectively.

To debug settings scripts in command-line, run the

    mvnDebug org.jetbrains.teamcity:teamcity-configs-maven-plugin:generate

command and attach your debugger to the port 8000.

To debug in IntelliJ Idea, open the 'Maven Projects' tool window (View
-> Tool Windows -> Maven Projects), find the generate task node
(Plugins -> teamcity-configs -> teamcity-configs:generate), the
'Debug' option is available in the context menu for the task.
*/

version = "2024.03"

project {
    description = "Intern Project Area"

    vcsRoot(TeamCityDistribution1)

    buildType(AllTests)
    buildType(Build1)
    buildType(Release)
}

object AllTests : BuildType({
    name = "All Tests"
    description = "Tests the FFM Target"
    paused = true

    artifactRules = "+:%codecoverage_path%"

    params {
        param("xcodebuild.scheme", "ForeFlight")
        select("iPhone.simulator", "iPhone 15 Pro Max", label = "iPhone Simulator", description = """xcrun simctl list devicetypes --json | jq '.devicetypes[] | select(.name | contains("iPhone")) | {name} | join(" ")'""", display = ParameterDisplay.PROMPT,
                options = listOf("iPhone 6s", "iPhone 6s Plus", "iPhone SE (1st generation)", "iPhone 7", "iPhone 7 Plus", "iPhone 8", "iPhone 8 Plus", "iPhone X", "iPhone Xs", "iPhone Xs Max", "iPhone Xʀ", "iPhone 11", "iPhone 11 Pro", "iPhone 11 Pro Max", "iPhone SE (2nd generation)", "iPhone 12 mini", "iPhone 12", "iPhone 12 Pro", "iPhone 12 Pro Max", "iPhone 13 Pro", "iPhone 13 Pro Max", "iPhone 13 mini", "iPhone 13", "iPhone SE (3rd generation)", "iPhone 14", "iPhone 14 Plus", "iPhone 14 Pro", "iPhone 14 Pro Max", "iPhone 15", "iPhone 15 Plus", "iPhone 15 Pro", "iPhone 15 Pro Max"))
        text("xcresults_path", "%system.teamcity.build.tempDir%/AllTests.xcresult", label = "XCResults Path", description = "Path to *.xcresults", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        param("xcodebuild.project_path", "ForeFlightMobile.xcworkspace")
        param("destination.name", "%xcode.simulator.name%")
        text("codecoverage_path", "%system.teamcity.build.tempDir%/%build.number%_xccovReport.json", label = "Code Coverage Report Path", description = "Path to code coverage report json", display = ParameterDisplay.HIDDEN, allowEmpty = true)
        param("destination.os", "%iOS.version%")
    }

    vcs {
        root(AbsoluteId("CiBuilds_Xcode_ForeFlightMobile_BitbucketIOS2"))

        cleanCheckout = true
    }

    steps {
        step {
            name = "Detect Xcode Version"
            id = "Detect_Xcode_Version"
            type = "select_xcode"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("project.yml.path", "project.yml")
            param("env.XCODE_VERSION", "")
            param("xcode_version.path", ".xcode-version")
            param("teamcity.step.phase", "")
        }
        step {
            name = "Create Developer Certificate"
            id = "Create_Developer_Certificate"
            type = "store_certificate"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("teamcity.step.phase", "")
            param("base64.certificate", "%system.base64.development_certificate%")
        }
        step {
            id = "Mobile_SetupKeychain"
            type = "Mobile_SetupKeychain"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("keychain.name", "%system.keychain.name%")
            param("keychain.password", "%system.keychain.password%")
            param("keychain.certificates_dir", "%system.keychain.certificates_dir%")
            param("keychain.unlocked", "true")
            param("teamcity.step.phase", "")
        }
        step {
            name = "Create iPhone Simulator"
            id = "Create_iPhone_Simulator"
            type = "create_simulator"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("simctl.create.runtime_id", "iOS%iOS.version%")
            param("teamcity.step.phase", "")
            param("simctl.create.simulator_name", "%xcode.simulator.name%")
            param("simctl.create.device_type_id", "%iPhone.simulator%")
        }
        exec {
            name = "Generate Xcode Project"
            id = "Generate_Xcode_Project"
            path = "xcodegen"
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
        }
        xcode {
            name = "Test"
            id = "Xcode"
            enabled = false
            projectPath = "ForeFlightMobile.xcworkspace"
            xcodePath = "%env.DEVELOPER_DIR%"
            buildType = schemeBased {
                scheme = "ForeFlight"
                outputDirectory = default()
            }
            buildActions = ""
            runTests = true
            additionalCommandLineParameters = """
                -destination "platform=%destination.platform%,OS=%destination.os%,name=%destination.name%"
                -derivedDataPath "%system.derived_data.path%"
                -resultBundlePath "%xcresults_path%"
                -enableCodeCoverage YES
                -onlyUsePackageVersionsFromResolvedFile
                -parallelizeTargets
                -hideShellScriptEnvironment
                -skipPackagePluginValidation
                -skipMacroValidation
                OTHER_CODE_SIGN_FLAGS="--keychain %system.keychain.name%"
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        script {
            name = "Produce Coverage JSON"
            id = "Produce_Coverage_JSON"
            scriptContent = """xcrun xccov view --report --json "%xcresults_path%" > "%codecoverage_path%""""
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        step {
            name = "Delete iPhone Simulator"
            id = "Delete_iPhone_Simulator"
            type = "delete_simulator"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            param("simctl.delete.simulator_name", "%xcode.simulator.name%")
            param("teamcity.step.phase", "")
        }
        step {
            id = "Delete_Keychain"
            type = "Delete_Keychain"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            param("keychain.name", "%system.keychain.name%")
            param("teamcity.step.phase", "")
        }
    }

    triggers {
        vcs {
            branchFilter = "+:pull/*"
        }
        vcs {
            branchFilter = """
                +:release/*
                +:firstrun/*
            """.trimIndent()
        }
        vcs {
            branchFilter = """
                +:develop
                +:alpha
                +:gh-readonly-queue/develop/*
                +:gh-readonly-queue/alpha/*
            """.trimIndent()
        }
    }

    failureConditions {
        supportTestRetry = true
    }

    features {
        pullRequests {
            vcsRootExtId = "CiBuilds_Xcode_ForeFlightMobile_BitbucketIOS2"
            provider = github {
                authType = vcsRoot()
                filterTargetBranch = """
                    +:refs/heads/alpha
                    +:refs/heads/develop
                    +:refs/heads/epic/*
                """.trimIndent()
                filterAuthorRole = PullRequests.GitHubRoleFilter.MEMBER
                ignoreDrafts = true
            }
        }
        commitStatusPublisher {
            vcsRootExtId = "CiBuilds_Xcode_ForeFlightMobile_BitbucketIOS2"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = vcsRoot()
            }
        }
    }

    requirements {
        equals("system.agent.name", "build-agent-26", "RQ_340")
    }
    
    disableSettings("RQ_340")
})

object Build1 : BuildType({
    name = "Build1"

    vcs {
        root(DslContext.settingsRoot)
    }

    triggers {
        vcs {
        }
    }

    features {
        perfmon {
        }
    }
})

object Release : BuildType({
    name = "Release"
    paused = true

    artifactRules = """
        +:archive/** => archive.zip
        +:enterprise/Apps/Payload/** => appArchive.zip
        +:derived_data/Logs/Build/** => buildLog.zip
        +:enterprise/app-thinning.plist
        +:enterprise/dSYMs.zip
        +:result_bundle.xcresult => %build.number%-results.xcresult.zip
        +:derived_data/Build/Intermediates.noindex/XCBuildData/** => %build.number%-build_data.zip
    """.trimIndent()
    maxRunningBuildsPerBranch = "*:2"

    params {
        password("app.store.password", "credentialsJSON:c5f175c2-f08f-47e9-99ea-acd58b52325c", label = "App Store Password", description = "App Store Connect Password", display = ParameterDisplay.HIDDEN, readOnly = true)
        param("google_services_plist", ".team_city_distribution/ffm/firebase/FEATURE.GoogleService-Info.plist")
        param("xcode.build.configuration", "Release")
        param("bundle.name", "ForeFlightMobile")
        checkbox("env.ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS", "true", label = "Enable Developmental Feature Flags", description = "Specifies the ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS to override default behavior of Development phase feature flags excluded from Release builds", display = ParameterDisplay.PROMPT,
                  checked = "true", unchecked = "false")
        text("build.bundle_id", "com.foreflight.ForeFlightMobile", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        text("build.xcarchive_name", "%system.teamcity.buildConfName% %build.number%.xcarchive", label = "Archive Name", description = "Name for .xcarchive", display = ParameterDisplay.HIDDEN, allowEmpty = false)
        select("app.version", "APPSTORE", label = "Build Type", description = "Controls where symbols are delivered", display = ParameterDisplay.PROMPT,
                options = listOf("AppStore" to "APPSTORE", "Alpha" to "ALPHA", "Develop" to "DEVELOP", "Feature" to "FEATURE", "Release Candidate" to "RC"))
        param("env.APP_VERSION", "15.6.1")
    }

    vcs {
        root(TeamCityDistribution1, "+:. => .tools")
        root(AbsoluteId("CiBuilds_Xcode_ForeFlightMobile_BitbucketIOS2"))
    }

    steps {
        step {
            name = "Create Development Certificate"
            id = "Create_Development_Certificate"
            type = "store_certificate"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("teamcity.step.phase", "")
            param("base64.certificate", "%system.base64.development_certificate%")
        }
        step {
            id = "Mobile_SetupKeychain"
            type = "Mobile_SetupKeychain"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("keychain.name", "%system.keychain.name%")
            param("keychain.password", "%system.keychain.password%")
            param("keychain.certificates_dir", "%system.keychain.certificates_dir%")
            param("keychain.unlocked", "true")
            param("teamcity.step.phase", "")
        }
        step {
            name = "Detect Xcode Version"
            id = "Detect_Xcode_Version"
            type = "select_xcode"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("project.yml.path", "project.yml")
            param("env.XCODE_VERSION", "")
            param("xcode_version.path", ".xcode-version")
            param("teamcity.step.phase", "")
        }
        script {
            name = "Generate Xcode Project"
            scriptContent = """
                #!/bin/zsh
                
                if [[ -f "project.yml" ]];then
                	xcodegen
                fi
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        exec {
            name = "Replace Icons"
            path = ".tools/updateIcon.sh"
            arguments = """
                -TEAMCITY_CHECKOUT_DIR "%teamcity.build.checkoutDir%" \
                -BRANCH_NAME "%teamcity.build.branch%"
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        xcode {
            name = "Archive"
            projectPath = "ForeFlightMobile.xcworkspace"
            xcodePath = "%env.DEVELOPER_DIR%"
            buildType = schemeBased {
                scheme = "ForeFlight"
                outputDirectory = default()
            }
            buildActions = "clean archive"
            additionalCommandLineParameters = """
                -configuration "%xcode.build.configuration%"
                -derivedDataPath "%teamcity.build.checkoutDir%/derived_data"
                -archivePath "%teamcity.build.checkoutDir%/archive/%build.xcarchive_name%"
                -resultBundlePath "%teamcity.build.checkoutDir%/result_bundle.xcresult"
                -hideShellScriptEnvironment
                -destination generic/platform=iOS
                -onlyUsePackageVersionsFromResolvedFile
                -showBuildTimingSummary
                -skipPackagePluginValidation
                -skipMacroValidation
                BUNDLE_IDENTIFIER="com.foreflight.%bundle.name%"
                BUILD_NUMBER="%build.number%"
                OTHER_CODE_SIGN_FLAGS="--keychain %system.keychain.name%"
                LD_GENERATE_MAP_FILE=YES
                LD_MAP_FILE_PATH=%teamcity.build.checkoutDir%/ios/LinkMaps/${'$'}(TARGET_TEMP_DIR)/${'$'}(PRODUCT_NAME)-LinkMap-${'$'}(CURRENT_VARIANT)-${'$'}(CURRENT_ARCH).txt
            """.trimIndent()
            param("org.jfrog.artifactory.selectedDeployableServer.useSpecs", "false")
            param("org.jfrog.artifactory.selectedDeployableServer.downloadSpecSource", "Job configuration")
            param("org.jfrog.artifactory.selectedDeployableServer.uploadSpecSource", "Job configuration")
        }
        step {
            name = "Upload Symbols (Crashlytics)"
            type = "upload_symbols"
            executionMode = BuildStep.ExecutionMode.DEFAULT
            param("firebase.verbose.enabled", "false")
            param("firebase.platform", "ios")
            param("firebase.dsyms_path", "archive/%build.xcarchive_name%/dSYMs")
            param("tools.path", ".tools")
            param("firebase.google_services_path", ".tools/ffm/firebase/%app.version%.GoogleService-Info.plist")
            param("teamcity.step.phase", "")
        }
        step {
            id = "Delete_Keychain"
            type = "Delete_Keychain"
            executionMode = BuildStep.ExecutionMode.ALWAYS
            param("keychain.name", "%system.keychain.name%")
            param("teamcity.step.phase", "")
        }
    }

    triggers {
        vcs {
            enabled = false
            triggerRules = """
                -:comment=nobuild:**
                -:comment=NOBUILD:**
            """.trimIndent()
            branchFilter = """
                +:*
                -:no-ci/*
                -:avalanche/*
                -:release/*
                -:develop
                -:firstrun/*
                -:gh-readonly-queue/*
            """.trimIndent()
        }
        retryBuild {
            enabled = false
            attempts = 1
            moveToTheQueueTop = true
        }
        vcs {
            enabled = false
            branchFilter = """
                +:release/*
                +:firstrun/*
            """.trimIndent()

            buildParams {
                checkbox("env.ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS", "false", label = "Enable Developmental Feature Flags", description = "Specifies the ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS to override default behavior of Development phase feature flags excluded from Release builds", display = ParameterDisplay.PROMPT,
                          checked = "true", unchecked = "false")
            }
        }
        vcs {
            enabled = false
            branchFilter = "+:alpha"
        }
        vcs {
            enabled = false
            branchFilter = "+:develop"

            buildParams {
                checkbox("env.ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS", "false", label = "Enable Developmental Feature Flags", description = "Specifies the ENABLE_FEATURE_FLAG_DEVELOPMENT_MACROS to override default behavior of Development phase feature flags excluded from Release builds", display = ParameterDisplay.PROMPT,
                          checked = "true", unchecked = "false")
            }
        }
    }

    failureConditions {
        executionTimeoutMin = 45
    }

    features {
        commitStatusPublisher {
            vcsRootExtId = "CiBuilds_Xcode_ForeFlightMobile_BitbucketIOS2"
            publisher = github {
                githubUrl = "https://api.github.com"
                authType = vcsRoot()
            }
        }
    }

    requirements {
        noLessThan("tools.xcode.version.major", "12")
        equals("teamcity.agent.jvm.os.arch", "aarch64")
        equals("system.agent.name", "build-agent-36", "RQ_384")
    }
    
    disableSettings("RQ_378", "RQ_384", "RUNNER_12", "RUNNER_13", "RUNNER_15", "RUNNER_232")
})

object TeamCityDistribution1 : GitVcsRoot({
    name = "TeamCity Distribution (1)"
    url = "https://github.com/foreflight/teamcity-ios-distribution.git"
    branch = "refs/heads/main"
    authMethod = password {
        userName = "jconnerly-foreflight"
        password = "credentialsJSON:19424b83-54ce-402b-8f72-1063fa942829"
    }
})
