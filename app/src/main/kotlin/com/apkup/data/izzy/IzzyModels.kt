package com.apkup.data.izzy

data class IzzyIndexResponse(
    val repo: IzzyRepoInfo? = null,
    val requests: IzzyRequestsInfo? = null,
    val apps: List<IzzyAppInfo> = emptyList(),
    val packages: Map<String, List<IzzyPackageInfo>> = emptyMap()
)

data class IzzyRepoInfo(
    val timestamp: Long = 0L,
    val version: Int = 0,
    val name: String = "",
    val description: String = "",
    val icon: String = "",
    val address: String = ""
)

data class IzzyRequestsInfo(
    val install: List<String> = emptyList(),
    val uninstall: List<String> = emptyList()
)

data class IzzyAppInfo(
    val packageName: String = "",
    val name: String = "",
    val summary: String = "",
    val description: String = "",
    val icon: String = "",
    val authorName: String = "",
    val categories: List<String> = emptyList(),
    val source: String? = null
)

data class IzzyPackageInfo(
    val versionName: String = "",
    val versionCode: Long = 0L,
    val size: Long = 0L,
    val apkName: String = "",
    val hash: String = "",
    val hashType: String = "",
    val signer: String = "",
    val minSdkVersion: Int = 0,
    val targetSdkVersion: Int = 0,
    val nativecode: List<String>? = null,
    val added: Long = 0L,
    val sig: String = ""
)
