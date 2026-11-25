package com.apkup.shared.desktop

import com.apkup.shared.model.KmpRepoBinding
import com.apkup.shared.network.KmpJson
import kotlinx.serialization.encodeToString
import java.io.File

object DesktopRepoCatalog {

    val builtInBindings: Map<String, KmpRepoBinding> = mapOf(
        "org.schabi.newpipe" to KmpRepoBinding("github", "TeamNewPipe/NewPipe"),
        "com.deniscerri.ytdl" to KmpRepoBinding("github", "deniscerri/ytdlnis"),
        "com.junkfood.seal" to KmpRepoBinding("github", "JunkFood02/Seal"),
        "app.revanced.manager.flutter" to KmpRepoBinding("github", "ReVanced/revanced-manager"),
        "app.mihon" to KmpRepoBinding("github", "mihonapp/mihon"),
        "oss.krtirtho.spotube" to KmpRepoBinding("github", "KRTirtho/spotube"),
        "dev.imranr.obtainium" to KmpRepoBinding("github", "ImranR98/Obtainium"),
        "com.kunzisoft.keepass.free" to KmpRepoBinding("github", "Kunzisoft/KeePassDX", assetFilterRegex = ".*-free\\.apk$"),
        "com.machiav3lli.backup" to KmpRepoBinding("github", "NeoApplications/Neo-Backup"),
        "app.lawnchair" to KmpRepoBinding("github", "LawnchairLauncher/lawnchair"),
        "ch.deletescape.lawnchair.plah" to KmpRepoBinding("github", "LawnchairLauncher/lawnchair"),
        "com.fsck.k9" to KmpRepoBinding("github", "thunderbird/thunderbird-android", assetFilter = "k9mail"),
        "net.thunderbird.android" to KmpRepoBinding("github", "thunderbird/thunderbird-android", assetFilter = "thunderbird"),
        "org.thunderbird.android" to KmpRepoBinding("github", "thunderbird/thunderbird-android", assetFilter = "thunderbird"),
        "im.vector.app" to KmpRepoBinding("github", "element-hq/element-android"),
        "org.briarproject.briar.android" to KmpRepoBinding("gitlab", "briar/briar", host = "code.briarproject.org"),
        "org.fdroid.fdroid" to KmpRepoBinding("gitlab", "fdroid/fdroidclient"),
        "com.aurora.store" to KmpRepoBinding("gitlab", "AuroraOSS/AuroraStore"),
        "org.videolan.vlc" to KmpRepoBinding("gitlab", "videolan/vlc-android", host = "code.videolan.org"),
        "org.thoughtcrime.securesms" to KmpRepoBinding("github", "signalapp/Signal-Android"),
        "com.vitorpamplona.amethyst" to KmpRepoBinding("github", "vitorpamplona/amethyst"),
        "org.fossify.gallery" to KmpRepoBinding("github", "FossifyOrg/Gallery"),
        "org.fossify.calendar" to KmpRepoBinding("github", "FossifyOrg/Calendar"),
        "org.fossify.contacts" to KmpRepoBinding("github", "FossifyOrg/Contacts"),
        "com.apkup" to KmpRepoBinding("github", "DurchEins/apkup")
    )

    private fun customBindingsFile(): File {
        val home = System.getProperty("user.home") ?: "."
        return File(File(home, ".apkup-desktop"), "bindings.json")
    }

    fun loadAllBindings(): MutableMap<String, KmpRepoBinding> {
        val merged = builtInBindings.toMutableMap()
        val file = customBindingsFile()
        if (file.isFile) {
            runCatching {
                val custom = KmpJson.instance.decodeFromString<Map<String, KmpRepoBinding>>(file.readText(Charsets.UTF_8))
                merged.putAll(custom)
            }
        }
        return merged
    }

    fun saveCustomBinding(packageName: String, binding: KmpRepoBinding) {
        val file = customBindingsFile()
        file.parentFile?.mkdirs()
        val currentCustom = if (file.isFile) {
            runCatching {
                KmpJson.instance.decodeFromString<MutableMap<String, KmpRepoBinding>>(file.readText(Charsets.UTF_8))
            }.getOrElse { mutableMapOf() }
        } else mutableMapOf()
        currentCustom[packageName] = binding
        file.writeText(KmpJson.instance.encodeToString(currentCustom), Charsets.UTF_8)
    }

    fun removeCustomBinding(packageName: String) {
        val file = customBindingsFile()
        if (!file.isFile) return
        val currentCustom = runCatching {
            KmpJson.instance.decodeFromString<MutableMap<String, KmpRepoBinding>>(file.readText(Charsets.UTF_8))
        }.getOrNull() ?: return
        currentCustom.remove(packageName)
        file.writeText(KmpJson.instance.encodeToString(currentCustom), Charsets.UTF_8)
    }
}
