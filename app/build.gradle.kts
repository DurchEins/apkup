import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.plugin.compose")
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

android {
    namespace = "com.apkup"
    compileSdk = 37

    val buildNumber = System.getenv("BUILD_NUMBER").orEmpty().trim()
    val parsedBuildNumber = buildNumber.takeIf { it.isNotEmpty() }?.let { raw ->
        raw.toIntOrNull()?.takeIf { it > 0 }
            ?: throw GradleException("BUILD_NUMBER must be a positive integer, got: '$raw'")
    }

    defaultConfig {
        applicationId = "com.apkup" + System.getenv("BUILD_TAG").orEmpty().trim()
        minSdk = 29
        targetSdk = 37
        versionCode = parsedBuildNumber ?: 223
        versionName = if (parsedBuildNumber == null) "1.45.1 (223)" else "1.45.1-$parsedBuildNumber"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            var configured = false

            // 1. Prefer environment variables (used in CI; immune to Properties backslash corruption)
            val envKsPath = System.getenv("ANDROID_KEYSTORE_PATH")?.trim().orEmpty()
            if (envKsPath.isNotEmpty()) {
                val envKsFile = File(envKsPath)
                val envStorePassword = System.getenv("ANDROID_KEYSTORE_PASSWORD").orEmpty()
                val envKeyAlias = System.getenv("ANDROID_KEY_ALIAS")?.trim().orEmpty()
                val envKeyPassword = System.getenv("ANDROID_KEY_PASSWORD").orEmpty()
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
                    val propFile = rootProject.file("local.properties")
                    if (propFile.isFile) {
                        val props = Properties()
                        FileInputStream(propFile).use { props.load(it) }
                        val ksPath = props.getProperty("keystore.file")?.trim()
                        if (!ksPath.isNullOrEmpty()) {
                            val ksFile = File(ksPath)
                            val resolvedFile = when {
                                ksFile.isAbsolute && ksFile.isFile -> ksFile
                                rootProject.file(ksPath).isFile -> rootProject.file(ksPath)
                                file(ksPath).isFile -> file(ksPath)
                                rootProject.file("app/$ksPath").isFile -> rootProject.file("app/$ksPath")
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
                val isCi = System.getenv("CI").equals("true", ignoreCase = true)
                val isReleaseTaskRequested = gradle.startParameter.taskNames.any {
                    it.contains("Release", ignoreCase = true)
                }
                if (isCi && isReleaseTaskRequested) {
                    throw GradleException(
                        "CRITICAL: Release signing keystore is not configured in CI! Refusing to sign release build with debug keystore."
                    )
                }
                val debugConfig = signingConfigs.getByName("debug")
                storeFile = debugConfig.storeFile
                storePassword = debugConfig.storePassword
                keyAlias = debugConfig.keyAlias
                keyPassword = debugConfig.keyPassword
                println("Local dev notice: Release keystore not configured; falling back to debug signing config.")
            }
            enableV2Signing = true
            enableV3Signing = true
        }
    }

    buildTypes {
        getByName("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
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
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
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

dependencies {
    constraints {
        implementation("com.google.protobuf:protobuf-javalite:4.31.1") {
            because("Enforce floor >= 4.28.2 on transitive protobuf from gplayapi to mitigate CVE-2024-7254")
        }
        implementation("com.google.protobuf:protobuf-java:4.31.1") {
            because("Enforce floor >= 4.28.2 if protobuf-java is ever pulled transitively")
        }
    }

    val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
    implementation(composeBom)
    implementation("androidx.activity:activity-compose:1.13.0")
    implementation("androidx.browser:browser:1.8.0")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-core")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.core:core-ktx:1.19.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.11.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
    implementation("androidx.navigation:navigation-compose:2.10.2")
    implementation("androidx.work:work-runtime-ktx:2.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.11.0")
    implementation("com.google.crypto.tink:tink-android:1.24.0")
    implementation("com.auroraoss:gplayapi:3.5.8")
    implementation("com.google.code.gson:gson:2.15.0")
    implementation("org.jsoup:jsoup:1.22.1")
    implementation("com.squareup.okhttp3:okhttp:5.5.1")
    implementation("com.squareup.retrofit2:converter-gson:3.0.0")
    implementation("com.squareup.retrofit2:retrofit:3.0.0")
    implementation("io.coil-kt.coil3:coil-compose:3.6.3")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.6.3")
    implementation("io.insert-koin:koin-android:4.2.2")
    implementation("io.insert-koin:koin-androidx-compose:4.2.2")
    implementation("dev.rikka.shizuku:api:13.1.5")
    implementation("dev.rikka.shizuku:provider:13.1.5")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.11.0")

    androidTestImplementation(composeBom)
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.7.0")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")

    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
