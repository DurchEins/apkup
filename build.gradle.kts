import java.io.File

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.kotlin.multiplatform.library) apply false
	alias(libs.plugins.kotlin.multiplatform) apply false
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.serialization) apply false
	alias(libs.plugins.kotlin.compose) apply false
	alias(libs.plugins.jetbrains.compose) apply false
}

val devCacheDir = File("D:/DevCache")
if (devCacheDir.isDirectory) {
	subprojects {
		layout.buildDirectory.set(
			File(devCacheDir, "apkup-build-kmp/${project.name}")
		)
	}
}
