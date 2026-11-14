package com.apkup.data.fdroid

import com.google.gson.annotations.SerializedName
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.Locale

data class FDroidRepoConfig(
    @SerializedName("id")
    val id: String = "",
    @SerializedName("name")
    val name: String = "",
    @SerializedName("url")
    val url: String = "",
    @SerializedName("fingerprint")
    val fingerprint: String = "",
    @SerializedName("enabled")
    val enabled: Boolean = false,
    @SerializedName("isBuiltIn")
    val isBuiltIn: Boolean = false,
    @SerializedName("isTofuPinned")
    val isTofuPinned: Boolean = false
) {
    val normalizedUrl: String
        get() = normalizeRepoBaseUrl(url) ?: url.trim().trimEnd('/')

    val normalizedFingerprint: String
        get() = normalizeFingerprint(fingerprint)

    companion object {
        const val ID_IZZYONDROID = "izzyondroid"
        const val ID_FDROID_OFFICIAL = "fdroid_official"
        const val ID_NEWPIPE = "newpipe"
        const val ID_MICROG = "microg"
        const val ID_GUARDIAN = "guardian_project"

        const val IZZY_FINGERPRINT = "3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A"
        const val FDROID_OFFICIAL_FINGERPRINT = "43238D512C1E5EB2D6569F4A3AFBF5523418B82E0A3ED1552770ABB9A9C9CCAB"
        const val NEWPIPE_FINGERPRINT = "E2402C78F9B97C6C89E97DB914A2751FDA1D02FE2039CC0897A462BDB57E7501"
        const val MICROG_FINGERPRINT = "9BD06727E62796C0130EB6DAB39B73157451582CBD138E86C468ACC395D14165"
        const val GUARDIAN_FINGERPRINT = "B7C2EEFD8DAC7806AF67DFCD92EB18126BC08312A7F2D6F3862E46013C7A6135"

        fun defaultPresets(): List<FDroidRepoConfig> = listOf(
            FDroidRepoConfig(
                id = ID_IZZYONDROID,
                name = "IzzyOnDroid",
                url = "https://apt.izzysoft.de/fdroid/repo",
                fingerprint = IZZY_FINGERPRINT,
                enabled = true,
                isBuiltIn = true
            ),
            FDroidRepoConfig(
                id = ID_FDROID_OFFICIAL,
                name = "F-Droid",
                url = "https://f-droid.org/repo",
                fingerprint = FDROID_OFFICIAL_FINGERPRINT,
                enabled = false,
                isBuiltIn = true
            ),
            FDroidRepoConfig(
                id = ID_NEWPIPE,
                name = "NewPipe Repo",
                url = "https://archive.newpipe.net/fdroid/repo",
                fingerprint = NEWPIPE_FINGERPRINT,
                enabled = false,
                isBuiltIn = true
            ),
            FDroidRepoConfig(
                id = ID_MICROG,
                name = "microG",
                url = "https://microg.org/fdroid/repo",
                fingerprint = MICROG_FINGERPRINT,
                enabled = false,
                isBuiltIn = true
            ),
            FDroidRepoConfig(
                id = ID_GUARDIAN,
                name = "Guardian Project",
                url = "https://guardianproject.info/fdroid/repo",
                fingerprint = GUARDIAN_FINGERPRINT,
                enabled = false,
                isBuiltIn = true
            )
        )

        fun normalizeFingerprint(raw: String?): String =
            raw.orEmpty()
                .replace(":", "")
                .replace(" ", "")
                .trim()
                .uppercase(Locale.ROOT)

        fun isValidSha256Fingerprint(hex: String): Boolean =
            hex.length == 64 && hex.all { it in '0'..'9' || it in 'A'..'F' }

        /**
         * Parses an F-Droid repository URL or share link such as:
         * `https://apt.izzysoft.de/fdroid/repo?fingerprint=3BF0D6...`
         * Strictly enforces `https://` (or `fdroidrepo://`) scheme and strips query/fragment
         * parameters from the base URL. Fingerprint is optional (TOFU-pinned on first verified JAR).
         */
        fun parseShareInput(
            rawUrl: String,
            explicitName: String = "",
            explicitFingerprint: String = "",
            requireFingerprint: Boolean = false
        ): Result<FDroidRepoConfig> {
            val trimmed = rawUrl.trim()
            val candidateUrl = if (trimmed.startsWith("fdroidrepo://", ignoreCase = true)) {
                "https://" + trimmed.substringAfter("://")
            } else {
                trimmed
            }
            val parsed = candidateUrl.toHttpUrlOrNull()
                ?: return Result.failure(IllegalArgumentException("Invalid HTTPS repository URL"))
            if (!parsed.scheme.equals("https", ignoreCase = true)) {
                return Result.failure(IllegalArgumentException("Only HTTPS F-Droid repositories are allowed"))
            }
            if (parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
                return Result.failure(IllegalArgumentException("Credentials in repository URL are not allowed"))
            }

            val queryFp = parsed.queryParameter("fingerprint")
                ?: parsed.queryParameter("FINGERPRINT")
            val chosenFp = normalizeFingerprint(explicitFingerprint.ifBlank { queryFp.orEmpty() })
            if (chosenFp.isNotEmpty() && !isValidSha256Fingerprint(chosenFp)) {
                return Result.failure(IllegalArgumentException("Repository fingerprint must be a 64-character SHA-256 hex string"))
            }

            val cleanPathSegments = parsed.pathSegments.filter { it.isNotEmpty() }
            val cleanUrlBuilder = parsed.newBuilder()
                .query(null)
                .fragment(null)
            val baseUrl = cleanUrlBuilder.build().toString().trimEnd('/')

            // Auto-fill known official fingerprint if user omitted ?fingerprint= on a known official repo URL
            val resolvedFp = chosenFp.ifEmpty {
                defaultPresets().firstOrNull {
                    it.normalizedUrl.equals(baseUrl, ignoreCase = true)
                }?.normalizedFingerprint.orEmpty()
            }
            if (requireFingerprint && resolvedFp.isEmpty()) {
                return Result.failure(
                    IllegalArgumentException("SHA-256 signing certificate fingerprint is required for custom F-Droid repositories")
                )
            }

            val displayName = explicitName.trim().ifEmpty {
                val hostLabel = parsed.host.removePrefix("www.")
                if (cleanPathSegments.isNotEmpty()) "$hostLabel (${cleanPathSegments.last()})" else hostLabel
            }
            val id = "custom_" + (baseUrl.lowercase(Locale.ROOT).hashCode().toUInt().toString(16))

            return Result.success(
                FDroidRepoConfig(
                    id = id,
                    name = displayName,
                    url = baseUrl,
                    fingerprint = resolvedFp,
                    enabled = true,
                    isBuiltIn = false,
                    isTofuPinned = false
                )
            )
        }

        fun normalizeRepoBaseUrl(rawUrl: String?): String? {
            val trimmed = rawUrl?.trim().orEmpty()
            val candidate = if (trimmed.startsWith("fdroidrepo://", ignoreCase = true)) {
                "https://" + trimmed.substringAfter("://")
            } else {
                trimmed
            }
            val parsed = candidate.toHttpUrlOrNull() ?: return null
            if (!parsed.scheme.equals("https", ignoreCase = true)) return null
            return parsed.newBuilder().query(null).fragment(null).build().toString().trimEnd('/')
        }
    }
}
