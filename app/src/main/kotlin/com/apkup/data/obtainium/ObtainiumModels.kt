package com.apkup.data.obtainium

import com.google.gson.annotations.SerializedName

/**
 * Represents an app entry in an Obtainium export file.
 * Obtainium exports either a JSON array: [ { ... }, { ... } ]
 * or an object containing an "apps" list: { "apps": [ { ... } ], "settings": { ... } }
 */
data class ObtainiumAppEntry(
    @SerializedName("id")
    val id: String = "", // Android package name, e.g. "org.schabi.newpipe"

    @SerializedName("url")
    val url: String = "", // e.g. "https://github.com/TeamNewPipe/NewPipe"

    @SerializedName("author")
    val author: String = "",

    @SerializedName("name")
    val name: String = "",

    @SerializedName("overrideSource")
    val overrideSource: String? = null,

    @SerializedName("version")
    val version: String? = null,

    @SerializedName("preferredApkIndex")
    val preferredApkIndex: Int? = null,

    @SerializedName("additionalSettings")
    val additionalSettings: String? = null
)

/**
 * Wrapper for Obtainium full exports with a settings root object.
 */
data class ObtainiumExportContainer(
    @SerializedName("apps")
    val apps: List<ObtainiumAppEntry> = emptyList(),

    @SerializedName("settings")
    val settings: Any? = null
)

/**
 * Result details returned after an Obtainium import operation.
 */
data class ObtainiumImportResult(
    val totalEntries: Int = 0,
    val importedBindingsCount: Int = 0,
    val skippedCount: Int = 0,
    val importedPackages: List<String> = emptyList()
)
