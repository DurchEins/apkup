package com.apkup.shared.engine

object KmpAssetSelector {

    const val MAX_REGEX_LENGTH = 256

    private val KNOWN_INCOMPATIBLE_ABIS = mapOf(
        "x86" to setOf("x86", "i386", "i686"),
        "x86_64" to setOf("x86_64", "x64", "amd64"),
        "armeabi-v7a" to setOf("armeabi-v7a", "armv7", "armv7a", "armeabi"),
        "arm64-v8a" to setOf("arm64-v8a", "arm64", "aarch64")
    )

    fun canonicalizeAbi(raw: String): String {
        val normalized = raw.trim().lowercase().replace('_', '-')
        return when (normalized) {
            "universal", "noarch", "any", "all", "arm64-v8a+armeabi-v7a" -> "universal"
            "arm64-v8a", "arm64", "aarch64", "arm64-v8" -> "arm64-v8a"
            "armeabi-v7a", "armeabi-v7", "armv7", "armv7a", "arm-v7a", "arm" -> "armeabi-v7a"
            "armeabi" -> "armeabi"
            "x86-64", "amd64", "x64" -> "x86_64"
            "x86", "i686", "i386", "ia32" -> "x86"
            else -> raw.trim().lowercase()
        }
    }

    fun supportsArchitecture(
        appArchitectures: List<String>,
        supportedAbis: List<String>,
        deviceArchitecture: String = ""
    ): Boolean {
        if (appArchitectures.isEmpty()) return true
        val canonicalCandidateAbis = appArchitectures
            .flatMap { it.split(',', '+', '/') }
            .map { canonicalizeAbi(it) }
            .filter { it.isNotEmpty() }
            .toSet()

        if (canonicalCandidateAbis.isEmpty() || "universal" in canonicalCandidateAbis) {
            return true
        }

        val canonicalSupported = buildSet {
            supportedAbis.mapTo(this) { canonicalizeAbi(it) }
            if (deviceArchitecture.isNotBlank()) {
                add(canonicalizeAbi(deviceArchitecture))
            }
        }
        return canonicalCandidateAbis.any { it in canonicalSupported }
    }

    fun compileSafeRegex(pattern: String?): Regex? {
        val trimmed = pattern?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        if (trimmed.length > MAX_REGEX_LENGTH) return null
        if (hasNestedQuantifierRisk(trimmed)) return null
        return runCatching { Regex(trimmed, RegexOption.IGNORE_CASE) }.getOrNull()
    }

    fun isValidRegex(pattern: String): Boolean {
        val trimmed = pattern.trim()
        if (trimmed.isEmpty()) return true
        return compileSafeRegex(trimmed) != null
    }

    private fun hasNestedQuantifierRisk(pattern: String): Boolean {
        val stack = ArrayDeque<Boolean>()
        var inCharClass = false
        var i = 0
        while (i < pattern.length) {
            val ch = pattern[i]
            if (ch == '\\') {
                i += 2
                continue
            }
            if (inCharClass) {
                if (ch == ']') inCharClass = false
                i++
                continue
            }
            when (ch) {
                '[' -> inCharClass = true
                '(' -> stack.addLast(false)
                ')' -> {
                    val innerQ = if (stack.isEmpty()) false else stack.removeLast()
                    val (outerQ, nextIdx) = consumeQuantifier(pattern, i + 1)
                    if (innerQ && outerQ) return true
                    if ((outerQ || innerQ) && stack.isNotEmpty()) {
                        stack[stack.lastIndex] = true
                    }
                    i = nextIdx
                    continue
                }
                '*', '+' -> if (stack.isNotEmpty()) stack[stack.lastIndex] = true
                '{' -> {
                    val (boundedQ, nextIdx) = consumeBraceQuantifier(pattern, i)
                    if (boundedQ) {
                        if (stack.isNotEmpty()) stack[stack.lastIndex] = true
                        i = nextIdx
                        continue
                    }
                }
            }
            i++
        }
        return false
    }

    private fun consumeQuantifier(pattern: String, startIndex: Int): Pair<Boolean, Int> {
        if (startIndex >= pattern.length) return false to startIndex
        return when (pattern[startIndex]) {
            '*', '+' -> true to skipModifier(pattern, startIndex + 1)
            '{' -> {
                val (isRep, nextIdx) = consumeBraceQuantifier(pattern, startIndex)
                if (isRep) true to skipModifier(pattern, nextIdx) else false to startIndex
            }
            else -> false to startIndex
        }
    }

    private fun consumeBraceQuantifier(pattern: String, startIndex: Int): Pair<Boolean, Int> {
        if (startIndex >= pattern.length || pattern[startIndex] != '{') return false to startIndex
        val close = pattern.indexOf('}', startIndex + 1)
        if (close == -1) return false to startIndex
        val body = pattern.substring(startIndex + 1, close)
        val parts = body.split(',')
        if (parts.size > 2) return false to startIndex
        val min = parts[0].toIntOrNull() ?: return false to startIndex
        val max = if (parts.size == 2) {
            if (parts[1].isEmpty()) Int.MAX_VALUE else parts[1].toIntOrNull() ?: return false to startIndex
        } else min
        return (max > 1) to (close + 1)
    }

    private fun skipModifier(pattern: String, idx: Int): Int =
        if (idx < pattern.length && (pattern[idx] == '?' || pattern[idx] == '+')) idx + 1 else idx

    fun <T> findBestApk(
        assets: List<T>,
        getFilename: (T) -> String,
        supportedAbis: List<String> = listOf("arm64-v8a", "armeabi-v7a"),
        requiredSubstring: String? = null,
        assetFilterRegex: String? = null,
        preferFossFlavor: Boolean = false
    ): T? {
        val reqLower = requiredSubstring?.trim()?.lowercase()?.takeIf { it.isNotEmpty() }
        val compiledRegex = if (assetFilterRegex.isNullOrBlank()) null else compileSafeRegex(assetFilterRegex) ?: return null

        val apkAssets = assets.filter { asset ->
            val fname = getFilename(asset)
            fname.endsWith(".apk", ignoreCase = true) &&
                (reqLower == null || fname.lowercase().contains(reqLower)) &&
                (compiledRegex == null || compiledRegex.containsMatchIn(fname))
        }
        if (apkAssets.isEmpty()) return null

        val deviceAbis = supportedAbis.map { it.lowercase() }
        val primaryAbi = deviceAbis.firstOrNull() ?: "arm64-v8a"

        val scored = apkAssets.mapNotNull { asset ->
            val filename = getFilename(asset).lowercase()
            if (filename.endsWith("-unaligned.apk") ||
                filename.endsWith("-sources.apk") ||
                filename.endsWith("-debug.apk") ||
                filename.endsWith("_debug.apk") ||
                filename.contains("-debug-")
            ) {
                return@mapNotNull null
            }

            var detectedAbi: String? = null
            for ((canonicalAbi, aliases) in KNOWN_INCOMPATIBLE_ABIS) {
                if (aliases.any { alias ->
                        val esc = Regex.escape(alias)
                        filename.contains(Regex("[._-]$esc[._-]|^$esc[._-]|[._-]$esc\\.apk$"))
                    }
                ) {
                    detectedAbi = canonicalAbi
                    break
                }
            }

            val baseScore = if (detectedAbi != null) {
                if (detectedAbi !in deviceAbis) return@mapNotNull null
                if (detectedAbi == primaryAbi) 100 else 80
            } else {
                if (filename.contains("universal")) 60 else 50
            }

            var adj = 0
            if (hasFlavorToken(filename, "debug")) adj -= 60
            if (hasFlavorToken(filename, "huawei")) adj -= 25
            if (preferFossFlavor) {
                if (listOf("foss", "libre", "fdroid", "nogms").any { hasFlavorToken(filename, it) }) adj += 15
                if (listOf("gplay", "gms", "playstore", "proprietary").any { hasFlavorToken(filename, it) }) adj -= 30
            }
            asset to (baseScore + adj)
        }

        return scored.maxByOrNull { it.second }?.first
    }

    private fun hasFlavorToken(lowercaseFilename: String, token: String): Boolean {
        val esc = Regex.escape(token)
        return lowercaseFilename.contains(Regex("(^|[._-])$esc([._-]|\\.apk$)"))
    }
}
