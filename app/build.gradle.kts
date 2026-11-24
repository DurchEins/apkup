import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.StringReader
import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.apkup"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    val buildNumber = providers.environmentVariable("BUILD_NUMBER").orNull.orEmpty().trim()
    val parsedBuildNumber = buildNumber.takeIf { it.isNotEmpty() }?.let { raw ->
        raw.toIntOrNull()?.takeIf { it > 0 }
            ?: throw GradleException("BUILD_NUMBER must be a positive integer, got: '$raw'")
    }
    val buildTag = providers.environmentVariable("BUILD_TAG").orNull.orEmpty().trim()

    defaultConfig {
        applicationId = "com.apkup" + buildTag
        minSdk = 29
        targetSdk = 37
        versionCode = parsedBuildNumber ?: 234
        versionName = if (parsedBuildNumber == null) "1.47.0 (234)" else "1.47.0-$parsedBuildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        manifestPlaceholders["appLabel"] = "@string/app_name"
    }

    signingConfigs {
        create("release") {
            var configured = false

            // 1. Prefer environment variables (used in CI; immune to Properties backslash corruption)
            val envKsPath = providers.environmentVariable("ANDROID_KEYSTORE_PATH").orNull?.trim().orEmpty()
            if (envKsPath.isNotEmpty()) {
                val envKsFile = File(envKsPath)
                val envStorePassword = providers.environmentVariable("ANDROID_KEYSTORE_PASSWORD").orNull.orEmpty()
                val envKeyAlias = providers.environmentVariable("ANDROID_KEY_ALIAS").orNull?.trim().orEmpty()
                val envKeyPassword = providers.environmentVariable("ANDROID_KEY_PASSWORD").orNull.orEmpty()
                if (envKsFile.isFile && envStorePassword.isNotEmpty() && envKeyAlias.isNotEmpty() && envKeyPassword.isNotEmpty()) {
                    storeFile = envKsFile
                    storePassword = envStorePassword
                    keyAlias = envKeyAlias
                    keyPassword = envKeyPassword
                    configured = true
                } else {
                    throw GradleException(
                        "ANDROID_KEYSTORE_PATH was set ('$envKsPath'), but keystore file or credentials are incomplete/missing."
                    )
                }
            }

            // 2. Fallback to root local.properties for local developer builds
            if (!configured) {
                try {
                    val propText = providers.fileContents(
                        rootProject.layout.projectDirectory.file("local.properties")
                    ).asText.orNull
                    if (propText != null) {
                        val props = Properties()
                        props.load(StringReader(propText))
                        val ksPath = props.getProperty("keystore.file")?.trim()
                        if (!ksPath.isNullOrEmpty()) {
                            val ksFile = File(ksPath)
                            val resolvedFile = when {
                                ksFile.isAbsolute && ksFile.isFile -> ksFile
                                rootProject.layout.projectDirectory.file(ksPath).asFile.isFile ->
                                    rootProject.layout.projectDirectory.file(ksPath).asFile
                                layout.projectDirectory.file(ksPath).asFile.isFile ->
                                    layout.projectDirectory.file(ksPath).asFile
                                rootProject.layout.projectDirectory.file("app/$ksPath").asFile.isFile ->
                                    rootProject.layout.projectDirectory.file("app/$ksPath").asFile
                                else -> null
                            }
                            val ksPass = props.getProperty("keystore.password")
                            val ksAlias = props.getProperty("keystore.keyalias")?.trim()
                            val keyPass = props.getProperty("keystore.keypassword")
                            if (resolvedFile != null && !ksPass.isNullOrEmpty() && !ksAlias.isNullOrEmpty() && !keyPass.isNullOrEmpty()) {
                                storeFile = resolvedFile
                                storePassword = ksPass
                                keyAlias = ksAlias
                                keyPassword = keyPass
                                configured = true
                            } else {
                                println("Keystore configuration in local.properties is incomplete or missing file '$ksPath'.")
                            }
                        }
                    }
                } catch (e: Exception) {
                    println("Warning: Failed to read keystore configuration from local.properties: ${e.message}")
                    configured = false
                }
            }

            if (!configured) {
                val isCi = providers.environmentVariable("CI").orNull.equals("true", ignoreCase = true)
                if (!isCi) {
                    val debugConfig = signingConfigs.getByName("debug")
                    storeFile = debugConfig.storeFile
                    storePassword = debugConfig.storePassword
                    keyAlias = debugConfig.keyAlias
                    keyPassword = debugConfig.keyPassword
                    println("Local dev notice: Release keystore not configured; falling back to debug signing config.")
                }
            }
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    testBuildType = if (providers.gradleProperty("a11yTest").isPresent) "a11y" else "debug"

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
        create("a11y") {
            initWith(getByName("debug"))
            applicationIdSuffix = ".a11y"
            versionNameSuffix = "-a11y"
            matchingFallbacks += listOf("debug")
            manifestPlaceholders["appLabel"] = "ApkUp A11y"
        }
        getByName("release") {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    packaging {
        resources {
            excludes += setOf(
                "/META-INF/{AL2.0,LGPL2.1}",
                "/META-INF/androidx/**/LICENSE.txt",
                "/META-INF/*.version",
                "DebugProbesKt.bin",
                "kotlin-tooling-metadata.json",
                "google/protobuf/*.proto",
            )
        }
    }

    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
        unitTests.all {
            it.maxHeapSize = "1024m"
        }
    }

    lint {
        abortOnError = true
        checkReleaseBuilds = true
        checkTestSources = false
        lintConfig = file("lint.xml")
    }
}

val isCiEnv = providers.environmentVariable("CI").map { it.equals("true", ignoreCase = true) }.getOrElse(false)
val hasCiKeystoreEnv = providers.environmentVariable("ANDROID_KEYSTORE_PATH").map { it.isNotBlank() }.getOrElse(false)
if (isCiEnv && !hasCiKeystoreEnv) {
    tasks.matching { it.name.contains("Release", ignoreCase = true) }.configureEach {
        doFirst {
            throw GradleException(
                "CRITICAL: Release signing keystore is not configured in CI! Refusing to sign release build with debug keystore."
            )
        }
    }
}

dependencies {
    constraints {
        implementation(libs.protobuf.javalite) {
            because("Enforce floor >= 4.28.2 on transitive protobuf from gplayapi to mitigate CVE-2024-7254")
        }
        implementation(libs.protobuf.java) {
            because("Enforce floor >= 4.28.2 if protobuf-java is ever pulled transitively")
        }
    }

    val composeBom = platform(libs.androidx.compose.bom)
    implementation(composeBom)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.browser)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.core)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.tink.android)
    implementation(libs.aurora.gplayapi)
    implementation(libs.gson)
    implementation(libs.jsoup)
    implementation(libs.okhttp)
    implementation(libs.retrofit.converter.gson)
    implementation(libs.retrofit)
    implementation(libs.coil.compose)
    implementation(libs.coil.network.okhttp)
    implementation(libs.koin.android)
    implementation(libs.koin.androidx.compose)
    implementation(libs.shizuku.api)
    implementation(libs.shizuku.provider)

    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)

    androidTestImplementation(composeBom)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.compose.ui.test.junit4.accessibility)
    androidTestImplementation(libs.androidx.test.espresso.core)
    androidTestImplementation(libs.androidx.test.ext.junit)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
    "a11yImplementation"(libs.androidx.compose.ui.test.manifest)
    "a11yImplementation"(libs.androidx.compose.ui.tooling)
}
