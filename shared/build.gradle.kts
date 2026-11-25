import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.multiplatform)
    alias(libs.plugins.android.kotlin.multiplatform.library)
    alias(libs.plugins.kotlin.serialization)
}

val jvmGplayAar by configurations.creating {
    isCanBeConsumed = false
    isTransitive = false
    attributes {
        attribute(org.gradle.api.attributes.Category.CATEGORY_ATTRIBUTE, objects.named(org.gradle.api.attributes.Category.LIBRARY))
        attribute(org.gradle.api.attributes.LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named("aar"))
        attribute(org.gradle.api.attributes.Usage.USAGE_ATTRIBUTE, objects.named(org.gradle.api.attributes.Usage.JAVA_RUNTIME))
    }
}

dependencies {
    jvmGplayAar(libs.aurora.gplayapi)
}

val extractGplayApiJar by tasks.registering(Copy::class) {
    from(jvmGplayAar.elements.map { files -> files.map { zipTree(it.asFile) } }) {
        include("classes.jar")
        rename("classes.jar", "gplayapi-4.0.0.jar")
    }
    into(layout.buildDirectory.dir("extracted-libs"))
}

kotlin {
    androidLibrary {
        namespace = "com.apkup.shared"
        compileSdk = 37
        minSdk = 29

        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    jvm {
        compilerOptions {
            jvmTarget.set(JvmTarget.JVM_17)
        }
    }

    sourceSets {
        commonMain.dependencies {
            api(libs.kotlinx.coroutines.core)
            api(libs.kotlinx.serialization.json)
            api(libs.ktor.client.core)
            implementation(libs.ktor.client.content.negotiation)
            implementation(libs.ktor.serialization.kotlinx.json)
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
            implementation(libs.kotlinx.coroutines.test)
        }
        androidMain {
            kotlin.srcDir("src/jvmCommon/kotlin")
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.kotlinx.coroutines.android)
            }
        }
        jvmMain {
            kotlin.srcDir("src/jvmCommon/kotlin")
            dependencies {
                implementation(libs.ktor.client.okhttp)
                implementation(libs.okhttp)
                api(files(extractGplayApiJar.map { File(it.destinationDir, "gplayapi-4.0.0.jar") }) {
                    builtBy(extractGplayApiJar)
                })
                api(libs.protobuf.javalite)
                implementation(libs.gson)
            }
        }
    }
}
