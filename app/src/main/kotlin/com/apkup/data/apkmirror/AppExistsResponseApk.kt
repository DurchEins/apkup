package com.apkup.data.apkmirror

import com.apkup.data.ui.ApkMirrorSource
import com.apkup.data.ui.AppInstalled
import com.apkup.data.ui.AppUpdate
import com.apkup.data.ui.Link
import com.google.gson.annotations.SerializedName

data class AppExistsResponseApk(
	@SerializedName("version_code") val versionCode: Long = 0,
	val link: String = "",
	@SerializedName("publish_date") val publishDate: String? = null,
	val arches: List<String> = emptyList(),
	val dpis: List<String>? = null,
	val minapi: String = "0",
	val description: String? = null,
	val capabilities: List<String>? = null,
	@SerializedName("signatures-sha1")
	val signaturesSha1: List<String>? = emptyList(),
	@SerializedName("signatures-sha256")
	val signaturesSha256: List<String>? = emptyList()
)

fun AppExistsResponseApk.toAppUpdate(app: AppInstalled, release: AppExistsResponseRelease) = AppUpdate(
	name = app.name,
	packageName = app.packageName,
	version = release.version,
	oldVersion = app.version,
	versionCode = versionCode,
	oldVersionCode = app.versionCode,
	source = ApkMirrorSource,
	link = Link.Url("https://www.apkmirror.com$link"),
	whatsNew = com.apkup.data.ui.cleanChangelogText(release.whatsNew?.takeIf { it.isNotBlank() } ?: description),
	updatedOn = publishDate?.takeIf { it.isNotBlank() } ?: release.publishDate.orEmpty(),
	installOrigin = app.installOrigin,
	installerPackageName = app.installerPackageName
)
