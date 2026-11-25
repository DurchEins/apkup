import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import java.io.File

plugins {
    alias(libs.plugins.android.library)
}

kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

val rustProjectDir: File = layout.projectDirectory.dir("rust").asFile
val generatedUniFfiFileDir: File = layout.buildDirectory.dir("generated/uniffi/kotlin").get().asFile
val jniLibsDir: File = layout.projectDirectory.dir("src/main/jniLibs").asFile

generatedUniFfiFileDir.mkdirs()
jniLibsDir.mkdirs()

val buildRustCoreAndroid = tasks.register<Exec>("buildRustCoreAndroid") {
    group = "rust"
    description = "Compiles apkup_rust_core for arm64-v8a via cargo-ndk and generates UniFFI Kotlin bindings"
    workingDir = rustProjectDir

    inputs.dir(File(rustProjectDir, "src"))
    inputs.file(File(rustProjectDir, "Cargo.toml"))
    outputs.dir(generatedUniFfiFileDir)

    val cargoHomeBin = "D:\\DevCache\\cargo\\bin"
    val sdkRoot = providers.environmentVariable("ANDROID_HOME").orNull
        ?: providers.environmentVariable("ANDROID_SDK_ROOT").orNull
        ?: "${providers.environmentVariable("LOCALAPPDATA").orNull ?: ""}\\Android\\Sdk"
    val ndkDir = File(sdkRoot, "ndk").listFiles()
        ?.filter { it.isDirectory }
        ?.maxByOrNull { it.name }
        ?.absolutePath
        ?: providers.environmentVariable("ANDROID_NDK_HOME").orNull.orEmpty()

    if (ndkDir.isNotBlank()) {
        environment("ANDROID_NDK_HOME", ndkDir)
    }
    val currentPath = providers.environmentVariable("PATH").orNull.orEmpty()
    environment("PATH", "$cargoHomeBin${File.pathSeparator}$currentPath")

    val outBindings = generatedUniFfiFileDir.absolutePath
    val jniOut = jniLibsDir.absolutePath
    val existingSo = File(jniLibsDir, "arm64-v8a/libapkup_rust_core.so").absolutePath
    val targetRoot = providers.environmentVariable("CARGO_TARGET_DIR").orNull?.takeIf { it.isNotBlank() }
        ?: File(rustProjectDir, "target").absolutePath
    val isWindows = System.getProperty("os.name").lowercase().contains("win")
    val hostLibFile = if (isWindows) {
        File(targetRoot, "debug/apkup_rust_core.dll").absolutePath
    } else {
        File(targetRoot, "debug/libapkup_rust_core.so").absolutePath
    }

    if (isWindows) {
        commandLine(
            "cmd", "/c",
            "(if not exist \"$existingSo\" cargo ndk -t arm64-v8a -o \"$jniOut\" build --release) && " +
                "(if not exist \"$hostLibFile\" cargo build --lib) && " +
                "cargo run --bin uniffi-bindgen -- generate --library \"$hostLibFile\" --crate apkup_rust_core --language kotlin --out-dir \"$outBindings\" --no-format"
        )
    } else {
        commandLine(
            "sh", "-c",
            "([ -f \"$existingSo\" ] || cargo ndk -t arm64-v8a -o \"$jniOut\" build --release) && " +
                "([ -f \"$hostLibFile\" ] || cargo build --lib) && " +
                "cargo run --bin uniffi-bindgen -- generate --library \"$hostLibFile\" --crate apkup_rust_core --language kotlin --out-dir \"$outBindings\" --no-format"
        )
    }
}

android {
    namespace = "com.apkup.core.rust"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 29
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    sourceSets["main"].kotlin.srcDir(generatedUniFfiFileDir)
}

tasks.named("preBuild") {
    dependsOn(buildRustCoreAndroid)
}

dependencies {
    implementation(project(":core-domain"))
    implementation(libs.jna.aar) {
        artifact {
            type = "aar"
        }
    }
    testImplementation(libs.junit)
    testImplementation("net.java.dev.jna:jna:5.14.0")
}
