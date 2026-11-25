import java.io.File

plugins {
	alias(libs.plugins.android.application) apply false
	alias(libs.plugins.android.library) apply false
	alias(libs.plugins.kotlin.jvm) apply false
	alias(libs.plugins.kotlin.compose) apply false
}

val devCacheDir = File("D:/DevCache")
if (devCacheDir.isDirectory) {
	subprojects {
		layout.buildDirectory.set(
			File(devCacheDir, "apkup-build-rust/${project.name}")
		)
	}
}
