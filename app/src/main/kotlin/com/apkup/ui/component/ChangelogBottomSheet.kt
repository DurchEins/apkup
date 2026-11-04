package com.apkup.ui.component

import android.content.Context
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apkup.BuildConfig
import com.apkup.R
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.Locale

enum class ChangelogBadgeType {
    MAJOR,
    FEATURE,
    PATCH
}

data class ChangelogBanner(
    val title: String,
    val badgeText: String = ""
)

data class ChangelogEntry(
    val icon: String,
    val title: String,
    val description: String
)

data class ChangelogRelease(
    val date: String,
    val version: String,
    val badgeLabel: String,
    val badgeType: ChangelogBadgeType,
    val banner: ChangelogBanner? = null,
    val entries: List<ChangelogEntry> = emptyList()
)

data class ChangelogMonthSection(
    val monthTitle: String,
    val releases: List<ChangelogRelease> = emptyList()
)

data class ChangelogDocument(
    val patchTitle: String? = null,
    val patchEntries: List<ChangelogEntry> = emptyList(),
    val featureTitle: String? = null,
    val featureEntries: List<ChangelogEntry> = emptyList(),
    val monthSections: List<ChangelogMonthSection> = emptyList()
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChangelogBottomSheet(
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val context = LocalContext.current
    val fallbackTitle = stringResource(R.string.changelog_title)
    val fallbackSubtitle = stringResource(R.string.changelog_menu_subtitle)
    val changelogDoc = remember(context, fallbackTitle, fallbackSubtitle) {
        val rawContent = loadChangelogContent(context)
        val parsed = parseChangelogDocument(rawContent)
        if (parsed.monthSections.isEmpty() && parsed.patchEntries.isEmpty() && parsed.featureEntries.isEmpty()) {
            val isGerman = Locale.getDefault().language.lowercase(Locale.ROOT) == "de"
            val defaultType = resolveBadgeType(BuildConfig.VERSION_NAME)
            val fallbackEntry = ChangelogEntry(
                icon = "•",
                title = fallbackTitle,
                description = fallbackSubtitle
            )
            ChangelogDocument(
                featureEntries = listOf(fallbackEntry),
                monthSections = listOf(
                    ChangelogMonthSection(
                        monthTitle = if (isGerman) "Aktuell" else "Current",
                        releases = listOf(
                            ChangelogRelease(
                                date = "",
                                version = BuildConfig.VERSION_NAME,
                                badgeLabel = defaultBadgeLabel(defaultType, isGerman),
                                badgeType = defaultType,
                                entries = listOf(fallbackEntry)
                            )
                        )
                    )
                )
            )
        } else {
            parsed
        }
    }

    val expandedMonths = remember(changelogDoc.monthSections.size) {
        mutableStateMapOf<Int, Boolean>().apply {
            changelogDoc.monthSections.indices.forEach { index ->
                put(index, index == 0)
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        tonalElevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .navigationBarsPadding()
        ) {
            // Top Header (Google "What's new" style with App Icon, Title & Close X button)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_changelog),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.changelog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AutoResizeText(
                        text = "ApkUp ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Close,
                        contentDescription = stringResource(R.string.changelog_button_dismiss),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)
            )

            // Scrollable Month Accordion List
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState())
            ) {
                changelogDoc.monthSections.forEachIndexed { monthIndex, section ->
                    val isExpanded = expandedMonths[monthIndex] ?: (monthIndex == 0)
                    ChangelogMonthAccordion(
                        section = section,
                        isExpanded = isExpanded,
                        onToggle = {
                            expandedMonths[monthIndex] = !isExpanded
                        }
                    )
                    if (monthIndex < changelogDoc.monthSections.lastIndex) {
                        HorizontalDivider(
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f)
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Bottom Dismiss Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(50.dp),
                shape = CircleShape
            ) {
                Text(
                    text = stringResource(R.string.changelog_button_dismiss),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(14.dp))
        }
    }
}

@Composable
private fun ChangelogMonthAccordion(
    section: ChangelogMonthSection,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .clickable(onClick = onToggle)
                .padding(vertical = 16.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = section.monthTitle,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Icon(
                imageVector = if (isExpanded) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }

        AnimatedVisibility(
            visible = isExpanded,
            enter = expandVertically() + fadeIn(),
            exit = shrinkVertically() + fadeOut()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
            ) {
                section.releases.forEachIndexed { releaseIndex, release ->
                    ChangelogReleaseBlock(release = release)
                    if (releaseIndex < section.releases.lastIndex) {
                        HorizontalDivider(
                            modifier = Modifier.padding(vertical = 20.dp),
                            thickness = 1.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChangelogReleaseBlock(release: ChangelogRelease) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 2.dp)
    ) {
        if (release.date.isNotBlank()) {
            Text(
                text = release.date,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
        }

        AutoResizeText(
            text = release.version,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Normal,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )

        Spacer(Modifier.height(10.dp))

        ChangelogPillBadge(
            label = release.badgeLabel,
            type = release.badgeType
        )

        if (release.banner != null) {
            Spacer(Modifier.height(14.dp))
            ChangelogFeatureBannerCard(banner = release.banner)
        }

        Spacer(Modifier.height(14.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            release.entries.forEach { entry ->
                ChangelogBulletRow(entry = entry)
            }
        }
    }
}

@Composable
private fun ChangelogPillBadge(
    label: String,
    type: ChangelogBadgeType
) {
    val (containerColor, contentColor) = when (type) {
        ChangelogBadgeType.FEATURE -> Color(0xFFA855F7) to Color(0xFF140524)
        ChangelogBadgeType.PATCH -> Color(0xFF38BDF8) to Color(0xFF062030)
        ChangelogBadgeType.MAJOR -> Color(0xFFF59E0B) to Color(0xFF241402)
    }

    Surface(
        shape = CircleShape,
        color = containerColor
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium.copy(fontSize = 12.sp),
            fontWeight = FontWeight.SemiBold,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 3.dp)
        )
    }
}

@Composable
private fun ChangelogFeatureBannerCard(banner: ChangelogBanner) {
    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF11131A),
            Color(0xFF151C30),
            Color(0xFF1D2B53)
        )
    )
    val glowBorderBrush = Brush.horizontalGradient(
        colors = listOf(
            Color(0xFF3B82F6).copy(alpha = 0.45f),
            Color(0xFF10B981).copy(alpha = 0.65f),
            Color(0xFFF59E0B).copy(alpha = 0.8f),
            Color(0xFFEF4444).copy(alpha = 0.8f),
            Color(0xFFA855F7).copy(alpha = 0.85f)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(126.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(bgBrush)
            .border(
                width = 1.dp,
                color = Color.White.copy(alpha = 0.08f),
                shape = RoundedCornerShape(16.dp)
            )
            .padding(horizontal = 18.dp, vertical = 16.dp),
        contentAlignment = Alignment.Center
    ) {
        // Subtle floating background card decoration (like Google's floating file icon)
        if (banner.badgeText.isNotBlank()) {
            Surface(
                shape = RoundedCornerShape(6.dp),
                color = Color(0xFFDBEAFE),
                shadowElevation = 4.dp,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 4.dp)
            ) {
                Text(
                    text = banner.badgeText,
                    style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1E40AF),
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                )
            }
        }

        // Center glowing capsule (inspired by the Google App screenshot banner)
        Row(
            modifier = Modifier
                .fillMaxWidth(0.92f)
                .clip(CircleShape)
                .background(Color(0xFF161922).copy(alpha = 0.92f))
                .border(
                    BorderStroke(1.5.dp, glowBorderBrush),
                    shape = CircleShape
                )
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFA855F7).copy(alpha = 0.22f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_changelog),
                    contentDescription = null,
                    tint = Color(0xFFD8B4FE),
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = banner.title,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = Color(0xFFF8FAFC),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun ChangelogBulletRow(entry: ChangelogEntry) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = buildAnnotatedString {
                if (entry.title.isNotBlank()) {
                    withStyle(
                        SpanStyle(
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        append(entry.title)
                        if (entry.description.isNotBlank()) {
                            append(" ")
                        }
                    }
                }
                withStyle(
                    SpanStyle(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                ) {
                    append(entry.description)
                }
            },
            style = MaterialTheme.typography.bodyMedium.copy(lineHeight = 20.sp),
            modifier = Modifier.weight(1f)
        )
    }
}

internal fun resolveBadgeType(version: String, customLabel: String? = null): ChangelogBadgeType {
    if (!customLabel.isNullOrBlank()) {
        val lower = customLabel.lowercase(Locale.ROOT)
        when {
            lower.contains("major") || lower.contains("hauptversion") -> return ChangelogBadgeType.MAJOR
            lower.contains("patch") || lower.contains("bugfix") || lower.contains("fix") || lower.contains("wartung") -> return ChangelogBadgeType.PATCH
            lower.contains("feature") || lower.contains("meilenstein") || lower.contains("milestone") -> return ChangelogBadgeType.FEATURE
        }
    }

    val semVer = version
        .removePrefix("v")
        .removePrefix("V")
        .substringBefore("-")
        .substringBefore("(")
        .trim()
    val parts = semVer.split(".")
    val minor = parts.getOrNull(1)?.toIntOrNull() ?: 0
    val patch = parts.getOrNull(2)?.toIntOrNull() ?: 0

    return when {
        parts.size >= 3 && patch > 0 -> ChangelogBadgeType.PATCH
        parts.size >= 2 && minor == 0 && patch == 0 -> ChangelogBadgeType.MAJOR
        else -> ChangelogBadgeType.FEATURE
    }
}

internal fun defaultBadgeLabel(type: ChangelogBadgeType, isGerman: Boolean): String {
    return when (type) {
        ChangelogBadgeType.MAJOR -> if (isGerman) "Major-Update" else "Major Update"
        ChangelogBadgeType.FEATURE -> if (isGerman) "Feature-Update" else "Feature Update"
        ChangelogBadgeType.PATCH -> if (isGerman) "Patch & Bugfix" else "Patch & Fix"
    }
}

internal fun resolveChangelogLanguageSuffix(locale: Locale = Locale.getDefault()): String {
    val lang = locale.language.lowercase(Locale.ROOT)
    return when {
        lang == "zh" -> "zh-rCN"
        lang in setOf("de", "en", "es", "fr", "pt", "it", "ru", "ja") -> lang
        else -> "en"
    }
}

internal fun resolveMilestonePrefix(versionName: String): String {
    val semVer = versionName.substringBefore("-").substringBefore("(").trim()
    val parts = semVer.split(".")
    return if (parts.size >= 2) "${parts[0]}.${parts[1]}" else semVer.ifEmpty { "1.39" }
}

internal fun loadChangelogContent(context: Context): String {
    val langSuffix = resolveChangelogLanguageSuffix(Locale.getDefault())
    val exactPrefix = BuildConfig.VERSION_NAME
        .substringBefore("-")
        .substringBefore("(")
        .trim()
        .ifEmpty { "1.39" }
    val milestonePrefix = resolveMilestonePrefix(BuildConfig.VERSION_NAME)

    val candidateAssets = listOf(
        "changelog/${exactPrefix}_${langSuffix}.md",
        "changelog/${milestonePrefix}_${langSuffix}.md",
        "changelog/${exactPrefix}_en.md",
        "changelog/${milestonePrefix}_en.md"
    ).distinct()

    for (assetPath in candidateAssets) {
        val text = runCatching {
            context.assets.open(assetPath).use { input ->
                BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
            }
        }.getOrNull()
        if (!text.isNullOrBlank()) {
            return text
        }
    }

    return runCatching {
        val files = context.assets.list("changelog").orEmpty()
        val matchingLang = files.filter { it.endsWith("_$langSuffix.md") }.maxOrNull()
            ?: files.filter { it.endsWith("_en.md") }.maxOrNull()
            ?: "1.39_en.md"
        context.assets.open("changelog/$matchingLang").use { input ->
            BufferedReader(InputStreamReader(input, Charsets.UTF_8)).readText()
        }
    }.getOrDefault("")
}

private enum class ChangelogSectionType {
    PATCH,
    FEATURES
}

internal fun cleanInlineMarkdown(text: String): String {
    return text
        .replace("`", "")
        .replace("**", "")
        .replace(Regex("""(?<!\*)\*([^\*\n]+)\*(?!\*)"""), "$1")
        .replace(Regex("""\s+"""), " ")
        .trim()
}

internal fun parseChangelogDocument(
    content: String,
    defaultVersionName: String = BuildConfig.VERSION_NAME
): ChangelogDocument {
    val hasReleaseSubheadings = content.lineSequence().any { it.trim().startsWith("### ") }
    if (hasReleaseSubheadings) {
        return parseMonthlyChangelogDocument(content, defaultVersionName)
    }
    return parseLegacyChangelogDocument(content, defaultVersionName)
}

private fun parseMonthlyChangelogDocument(
    content: String,
    defaultVersionName: String
): ChangelogDocument {
    val boldRegex = Regex("""^(?:(\S+)\s+)?\*\*(.+?)\*\*(?::)?\s*(.*)$""")
    val isGermanDoc = content.contains("Was ist neu", ignoreCase = true) ||
        content.contains("Oktober", ignoreCase = true) ||
        content.contains("Verbesserungen", ignoreCase = true)

    val monthSections = mutableListOf<ChangelogMonthSection>()
    var currentMonthTitle: String? = null
    var currentReleases = mutableListOf<ChangelogRelease>()

    var currentReleaseDate: String? = null
    var currentReleaseVersion: String = defaultVersionName
    var currentReleaseBadgeLabel: String = ""
    var currentReleaseBadgeType: ChangelogBadgeType = resolveBadgeType(defaultVersionName)
    var currentReleaseBanner: ChangelogBanner? = null
    var currentReleaseEntries = mutableListOf<ChangelogEntry>()

    fun flushCurrentRelease() {
        val date = currentReleaseDate
        if (date != null || currentReleaseEntries.isNotEmpty()) {
            val resolvedType = resolveBadgeType(currentReleaseVersion, currentReleaseBadgeLabel)
            val resolvedLabel = currentReleaseBadgeLabel.ifBlank {
                defaultBadgeLabel(resolvedType, isGermanDoc)
            }
            currentReleases.add(
                ChangelogRelease(
                    date = date.orEmpty(),
                    version = currentReleaseVersion,
                    badgeLabel = resolvedLabel,
                    badgeType = resolvedType,
                    banner = currentReleaseBanner,
                    entries = currentReleaseEntries.toList()
                )
            )
        }
        currentReleaseDate = null
        currentReleaseBanner = null
        currentReleaseEntries = mutableListOf()
    }

    fun flushCurrentMonth() {
        flushCurrentRelease()
        val month = currentMonthTitle
        if (!month.isNullOrBlank() && currentReleases.isNotEmpty()) {
            monthSections.add(
                ChangelogMonthSection(
                    monthTitle = month,
                    releases = currentReleases.toList()
                )
            )
        }
        currentReleases = mutableListOf()
    }

    for (line in content.lines()) {
        val trimmed = line.trim()
        when {
            trimmed.startsWith("## ") -> {
                flushCurrentMonth()
                currentMonthTitle = cleanInlineMarkdown(trimmed.removePrefix("##").trim())
            }
            trimmed.startsWith("### ") -> {
                flushCurrentRelease()
                val rawRelease = trimmed.removePrefix("###").trim()
                val segments = rawRelease.split("|").map { it.trim() }
                currentReleaseDate = cleanInlineMarkdown(segments.getOrNull(0).orEmpty())
                currentReleaseVersion = cleanInlineMarkdown(
                    segments.getOrNull(1)?.takeIf { it.isNotBlank() } ?: defaultVersionName
                )
                val explicitBadge = cleanInlineMarkdown(segments.getOrNull(2).orEmpty())
                currentReleaseBadgeType = resolveBadgeType(currentReleaseVersion, explicitBadge)
                currentReleaseBadgeLabel = explicitBadge.ifBlank {
                    defaultBadgeLabel(currentReleaseBadgeType, isGermanDoc)
                }
            }
            trimmed.startsWith("> [Banner]", ignoreCase = true) ||
                trimmed.startsWith("> [Banner:", ignoreCase = true) -> {
                val rawBanner = trimmed
                    .removePrefix(">")
                    .trim()
                    .removePrefix("[Banner]")
                    .removePrefix("[banner]")
                    .removePrefix("[Banner:")
                    .removePrefix("[banner:")
                    .removeSuffix("]")
                    .trim()
                val parts = rawBanner.split("|").map { cleanInlineMarkdown(it) }
                val bannerTitle = parts.getOrNull(0).orEmpty()
                val bannerBadge = parts.getOrNull(1).orEmpty()
                if (bannerTitle.isNotBlank()) {
                    currentReleaseBanner = ChangelogBanner(
                        title = bannerTitle,
                        badgeText = bannerBadge
                    )
                }
            }
            trimmed.startsWith("- ") || trimmed.startsWith("* ") -> {
                val itemText = trimmed.substring(2).trim()
                val match = boldRegex.find(itemText)
                val entry = if (match != null) {
                    val icon = match.groupValues[1].ifBlank { "•" }
                    val title = cleanInlineMarkdown(match.groupValues[2])
                        .removeSuffix(":")
                        .trim()
                    val description = cleanInlineMarkdown(match.groupValues[3])
                        .removePrefix(":")
                        .removePrefix("—")
                        .removePrefix("–")
                        .removePrefix("-")
                        .trim()
                    ChangelogEntry(icon = icon, title = title, description = description)
                } else {
                    ChangelogEntry(icon = "•", title = "", description = cleanInlineMarkdown(itemText))
                }
                currentReleaseEntries.add(entry)
            }
        }
    }

    flushCurrentMonth()

    val allReleases = monthSections.flatMap { it.releases }
    val firstRelease = allReleases.firstOrNull()
    val patchEntries = if (firstRelease?.badgeType == ChangelogBadgeType.PATCH) {
        firstRelease.entries
    } else {
        emptyList()
    }
    val featureEntries = if (firstRelease?.badgeType == ChangelogBadgeType.PATCH) {
        allReleases.drop(1).flatMap { it.entries }
    } else {
        allReleases.flatMap { it.entries }
    }

    return ChangelogDocument(
        patchTitle = firstRelease?.takeIf { it.badgeType == ChangelogBadgeType.PATCH }?.let { "${it.date} (${it.version})" },
        patchEntries = patchEntries,
        featureTitle = monthSections.firstOrNull()?.monthTitle,
        featureEntries = featureEntries,
        monthSections = monthSections
    )
}

private fun parseLegacyChangelogDocument(
    content: String,
    defaultVersionName: String
): ChangelogDocument {
    var patchTitle: String? = null
    val patchEntries = mutableListOf<ChangelogEntry>()
    var featureTitle: String? = null
    val featureEntries = mutableListOf<ChangelogEntry>()
    var currentSection = ChangelogSectionType.FEATURES

    val boldRegex = Regex("""^(?:(\S+)\s+)?\*\*(.+?)\*\*(?::)?\s*(.*)$""")

    for (line in content.lines()) {
        val trimmed = line.trim()
        if (trimmed.startsWith("## ")) {
            val rawHeading = trimmed.removePrefix("##").trim()
            val isPatchHeading = rawHeading.startsWith("[Patch]", ignoreCase = true) ||
                rawHeading.startsWith("Patch:", ignoreCase = true) ||
                rawHeading.contains("Patch", ignoreCase = true) ||
                rawHeading.startsWith("🛠️")
            if (isPatchHeading) {
                currentSection = ChangelogSectionType.PATCH
                patchTitle = cleanInlineMarkdown(
                    rawHeading
                        .removePrefix("[Patch]")
                        .removePrefix("[patch]")
                        .removePrefix("Patch:")
                )
            } else {
                currentSection = ChangelogSectionType.FEATURES
                featureTitle = cleanInlineMarkdown(
                    rawHeading
                        .removePrefix("[Features]")
                        .removePrefix("[features]")
                        .removePrefix("Features:")
                )
            }
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val itemText = trimmed.substring(2).trim()
            val match = boldRegex.find(itemText)
            val entry = if (match != null) {
                val icon = match.groupValues[1].ifBlank { "✨" }
                val title = cleanInlineMarkdown(match.groupValues[2])
                    .removeSuffix(":")
                    .trim()
                val description = cleanInlineMarkdown(match.groupValues[3])
                    .removePrefix(":")
                    .removePrefix("—")
                    .removePrefix("–")
                    .removePrefix("-")
                    .trim()
                ChangelogEntry(icon = icon, title = title, description = description)
            } else {
                ChangelogEntry(icon = "•", title = "", description = cleanInlineMarkdown(itemText))
            }
            if (currentSection == ChangelogSectionType.PATCH) {
                patchEntries.add(entry)
            } else {
                featureEntries.add(entry)
            }
        }
    }

    val isGermanDoc = (patchTitle.orEmpty() + featureTitle.orEmpty()).let {
        it.contains("aktuellen", ignoreCase = true) || it.contains(" aus ", ignoreCase = true)
    }
    val synthesizedReleases = buildList {
        if (patchEntries.isNotEmpty()) {
            add(
                ChangelogRelease(
                    date = patchTitle.orEmpty(),
                    version = defaultVersionName,
                    badgeLabel = defaultBadgeLabel(ChangelogBadgeType.PATCH, isGermanDoc),
                    badgeType = ChangelogBadgeType.PATCH,
                    entries = patchEntries
                )
            )
        }
        if (featureEntries.isNotEmpty()) {
            add(
                ChangelogRelease(
                    date = featureTitle.orEmpty(),
                    version = resolveMilestonePrefix(defaultVersionName) + ".0-Beta",
                    badgeLabel = defaultBadgeLabel(ChangelogBadgeType.FEATURE, isGermanDoc),
                    badgeType = ChangelogBadgeType.FEATURE,
                    entries = featureEntries
                )
            )
        }
    }
    val synthesizedMonths = if (synthesizedReleases.isNotEmpty()) {
        listOf(
            ChangelogMonthSection(
                monthTitle = featureTitle ?: patchTitle ?: "Changelog",
                releases = synthesizedReleases
            )
        )
    } else {
        emptyList()
    }

    return ChangelogDocument(
        patchTitle = patchTitle,
        patchEntries = patchEntries,
        featureTitle = featureTitle,
        featureEntries = featureEntries,
        monthSections = synthesizedMonths
    )
}

internal fun parseChangelogMarkdown(content: String): List<ChangelogEntry> {
    val doc = parseChangelogDocument(content)
    return doc.patchEntries + doc.featureEntries
}
