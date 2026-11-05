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
}
