package com.apkup.data.obtainium

import com.apkup.data.repomapping.RepoBinding
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ObtainiumManagerTest {

    @Test
    fun isObtainiumJsonDetectsArrayFormat() {
        val json = """
            [
              {
                "id": "org.schabi.newpipe",
                "url": "https://github.com/TeamNewPipe/NewPipe",
                "author": "TeamNewPipe",
                "name": "NewPipe",
                "overrideSource": "GitHub"
              }
            ]
        """.trimIndent()
        assertTrue(ObtainiumManager.isObtainiumJson(json))
    }

    @Test
    fun isObtainiumJsonDetectsContainerFormat() {
        val json = """
            {
              "apps": [
                {
                  "id": "org.videolan.vlc",
                  "url": "https://gitlab.com/videolan/vlc-android",
                  "author": "videolan",
                  "name": "VLC"
                }
              ],
              "settings": {}
            }
        """.trimIndent()
        assertTrue(ObtainiumManager.isObtainiumJson(json))
    }

    @Test
    fun isObtainiumJsonRejectsApkUpBackup() {
        val apkupBackup = """
            {
              "schemaVersion": 1,
              "exportedAtMillis": 1730000000000,
              "showSystemApps": false,
              "useGitHub": true
            }
        """.trimIndent()
        assertFalse(ObtainiumManager.isObtainiumJson(apkupBackup))
    }

    @Test
    fun isObtainiumJsonRejectsInvalidStrings() {
        assertFalse(ObtainiumManager.isObtainiumJson(""))
        assertFalse(ObtainiumManager.isObtainiumJson("random text"))
        assertFalse(ObtainiumManager.isObtainiumJson("{\"foo\": \"bar\"}"))
    }

    @Test
    fun parseObtainiumAppsExtractsCorrectEntries() {
        val json = """
            [
              {
                "id": "org.schabi.newpipe",
                "url": "https://github.com/TeamNewPipe/NewPipe/releases",
                "author": "TeamNewPipe",
                "name": "NewPipe",
                "overrideSource": "GitHub"
              },
              {
                "id": "com.vitorpamplona.amethyst",
                "url": "https://github.com/vitorpamplona/amethyst",
                "author": "vitorpamplona",
                "name": "Amethyst"
              }
            ]
        """.trimIndent()

        val apps = ObtainiumManager.parseObtainiumApps(json)
        assertEquals(2, apps.size)
        assertEquals("org.schabi.newpipe", apps[0].id)
        assertEquals("NewPipe", apps[0].name)
        assertEquals("com.vitorpamplona.amethyst", apps[1].id)
    }

    @Test
    fun convertUrlToBindingHandlesVariousFormats() {
        // GitHub standard
        val gh1 = ObtainiumManager.convertUrlToBinding("https://github.com/TeamNewPipe/NewPipe")
        assertNotNull(gh1)
        assertEquals("github", gh1!!.type)
        assertEquals("TeamNewPipe/NewPipe", gh1.repo)

        // GitHub with releases path
        val gh2 = ObtainiumManager.convertUrlToBinding("https://github.com/TeamNewPipe/NewPipe/releases/latest")
        assertNotNull(gh2)
        assertEquals("github", gh2!!.type)
        assertEquals("TeamNewPipe/NewPipe", gh2.repo)

        // GitHub slug
        val gh3 = ObtainiumManager.convertUrlToBinding("TeamNewPipe/NewPipe")
        assertNotNull(gh3)
        assertEquals("github", gh3!!.type)
        assertEquals("TeamNewPipe/NewPipe", gh3.repo)

        // GitLab standard
        val gl1 = ObtainiumManager.convertUrlToBinding("https://gitlab.com/videolan/vlc-android")
        assertNotNull(gl1)
        assertEquals("gitlab", gl1!!.type)
        assertEquals("videolan/vlc-android", gl1.repo)
        assertEquals("gitlab.com", gl1.resolvedHost)

        // GitLab with /-/releases
        val gl2 = ObtainiumManager.convertUrlToBinding("https://gitlab.com/videolan/vlc-android/-/releases")
        assertNotNull(gl2)
        assertEquals("gitlab", gl2!!.type)
        assertEquals("videolan/vlc-android", gl2.repo)

        // Self-hosted GitLab / KDE
        val kde = ObtainiumManager.convertUrlToBinding("https://invent.kde.org/network/tokodon/-/releases")
        assertNotNull(kde)
        assertEquals("gitlab", kde!!.type)
        assertEquals("network/tokodon", kde.repo)
        assertEquals("invent.kde.org", kde.resolvedHost)

        // Invalid
        assertNull(ObtainiumManager.convertUrlToBinding("not_a_valid_url"))
    }

    @Test
    fun exportObtainiumGeneratesValidJson() {
        val bindings = mapOf(
            "org.schabi.newpipe" to RepoBinding("github", "TeamNewPipe/NewPipe"),
            "org.videolan.vlc" to RepoBinding("gitlab", "videolan/vlc-android", "gitlab.com")
        )
        val names = mapOf(
            "org.schabi.newpipe" to "NewPipe",
            "org.videolan.vlc" to "VLC"
        )

        val exportedJson = ObtainiumManager.exportObtainium(bindings, names)
        assertTrue(ObtainiumManager.isObtainiumJson(exportedJson))

        val parsed = ObtainiumManager.parseObtainiumApps(exportedJson)
        assertEquals(2, parsed.size)
        val newPipe = parsed.first { it.id == "org.schabi.newpipe" }
        assertEquals("https://github.com/TeamNewPipe/NewPipe", newPipe.url)
        assertEquals("NewPipe", newPipe.name)
        assertEquals("TeamNewPipe", newPipe.author)
    }

    @Test
    fun convertUrlToBindingSupportsCodebergAndSkipsFDroidRepos() {
        val cb = ObtainiumManager.convertUrlToBinding("https://codeberg.org/freeyourgadget/Gadgetbridge/releases")
        assertNotNull(cb)
        assertEquals("codeberg", cb!!.type)
        assertEquals("freeyourgadget/Gadgetbridge", cb.repo)
        assertEquals("codeberg.org", cb.resolvedHost)

        val forgejo = ObtainiumManager.convertUrlToBinding(
            url = "https://git.disroot.org/org/app",
            overrideSource = "Codeberg"
        )
        assertNotNull(forgejo)
        assertEquals("codeberg", forgejo!!.type)
        assertEquals("org/app", forgejo.repo)
        assertEquals("git.disroot.org", forgejo.resolvedHost)

        // F-Droid repo URLs should not become GitHub bindings
        assertNull(ObtainiumManager.convertUrlToBinding("https://f-droid.org/repo", "FDroidThirdPartyRepo"))
        assertNull(ObtainiumManager.convertUrlToBinding("fdroidrepo://apt.izzysoft.de/fdroid/repo?fingerprint=ABCD"))
    }

    @Test
    fun buildImportPreviewSeparatesMappingsFDroidReposAndUninstalledWatchedApps() {
        val validFp64 = "626c860f384a98b5626c860f384a98b5626c860f384a98b5626c860f384a98b5"
        val rawJson = """
            {
              "apps": [
                {
                  "id": "org.schabi.newpipe",
                  "url": "https://github.com/TeamNewPipe/NewPipe",
                  "name": "NewPipe"
                },
                {
                  "id": "nodomain.freeyourgadget.gadgetbridge",
                  "url": "https://codeberg.org/Freeyourgadget/Gadgetbridge",
                  "name": "Gadgetbridge",
                  "overrideSource": "Codeberg"
                },
                {
                  "id": "fdroid.repo.custom",
                  "url": "fdroidrepo://repo.example.org/android/fdroid/repo?fingerprint=${validFp64.uppercase()}",
                  "name": "Custom Repo",
                  "overrideSource": "FDroidThirdPartyRepo"
                }
              ]
            }
        """.trimIndent()

        val preview = ObtainiumManager.buildImportPreview(
            rawJson = rawJson,
            installedPackageNames = setOf("org.schabi.newpipe")
        )

        assertEquals(3, preview.totalEntries)
        assertEquals(1, preview.matchedInstalledBindings.size)
        assertTrue(preview.matchedInstalledBindings.containsKey("org.schabi.newpipe"))
        assertEquals(1, preview.uninstalledCandidateApps.size)
        assertEquals("nodomain.freeyourgadget.gadgetbridge", preview.uninstalledCandidateApps.first().packageName)
        assertTrue(preview.uninstalledCandidateApps.first().binding.isCodeberg)
        assertEquals(1, preview.detectedFDroidRepos.size)
        assertEquals("https://repo.example.org/android/fdroid/repo", preview.detectedFDroidRepos.first().baseUrl)
        assertEquals(validFp64, preview.detectedFDroidRepos.first().fingerprint)
    }

    @Test
    fun gitRepoNamedRepoIsNotMisclassifiedAsFDroidAndShortFingerprintsAreRejected() {
        val gitRepoNamedRepo = ObtainiumAppEntry(
            id = "org.example.gitrepo",
            url = "https://github.com/myorg/repo",
            name = "MyOrg Repo"
        )
        assertNull(ObtainiumManager.parseFDroidRepoFromEntry(gitRepoNamedRepo))

        val preview = ObtainiumManager.buildImportPreview(
            rawJson = """[{"id":"org.example.gitrepo","url":"https://github.com/myorg/repo","name":"MyOrg Repo"}]""",
            installedPackageNames = setOf("org.example.gitrepo")
        )
        assertTrue(preview.detectedFDroidRepos.isEmpty())
        val binding = preview.matchedInstalledBindings["org.example.gitrepo"]
        assertNotNull(binding)
        assertTrue(binding!!.isGitHub)
        assertEquals("myorg/repo", binding.repo)

        val shortFpEntry = ObtainiumAppEntry(
            id = "fdroid.repo.shortfp",
            url = "fdroidrepo://repo.example.org/fdroid/repo?fingerprint=626C860F384A98B5",
            name = "Short FP",
            overrideSource = "FDroidThirdPartyRepo"
        )
        val parsedShort = ObtainiumManager.parseFDroidRepoFromEntry(shortFpEntry)
        assertNotNull(parsedShort)
        assertEquals("", parsedShort!!.fingerprint)
    }

    @Test
    fun exportObtainiumPreservesAssetFilterRegexInAdditionalSettings() {
        val bindings = mapOf(
            "org.example.filtered" to RepoBinding(
                type = "github",
                repo = "owner/app",
                assetFilterRegex = "arm64.*\\.apk$"
            ),
            "org.example.plain" to RepoBinding(
                type = "codeberg",
                repo = "owner/plain"
            )
        )
        val exported = ObtainiumManager.exportObtainium(bindings)
        val entries = ObtainiumManager.parseObtainiumApps(exported)
        val filtered = entries.first { it.id == "org.example.filtered" }
        assertNotNull(filtered.additionalSettings)

        val reimported = ObtainiumManager.buildImportPreview(
            rawJson = exported,
            installedPackageNames = setOf("org.example.filtered", "org.example.plain")
        )
        assertEquals("arm64.*\\.apk$", reimported.matchedInstalledBindings["org.example.filtered"]?.assetFilterRegex)
        assertNull(reimported.matchedInstalledBindings["org.example.plain"]?.assetFilterRegex)
    }
}
