package com.apkup.ui.component

import android.content.Context
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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

data class ChangelogEntry(
    val icon: String,
    val title: String,
    val description: String
)

data class ChangelogDocument(
    val patchTitle: String? = null,
    val patchEntries: List<ChangelogEntry> = emptyList(),
    val featureTitle: String? = null,
    val featureEntries: List<ChangelogEntry> = emptyList()
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
        if (parsed.patchEntries.isEmpty() && parsed.featureEntries.isEmpty()) {
            ChangelogDocument(
                featureEntries = listOf(
                    ChangelogEntry(
                        icon = "✨",
                        title = fallbackTitle,
                        description = fallbackSubtitle
                    )
                )
            )
        } else {
            parsed
        }
    }
    val shortPatchBadge = remember {
        "v" + BuildConfig.VERSION_NAME.substringBefore("-").substringBefore("(").trim()
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
            // Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_changelog),
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.changelog_title),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    AutoResizeText(
                        text = "ApkUp ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1
                    )
                }
            }

            // Scrollable Content
            Column(
                modifier = Modifier
                    .weight(1f, fill = false)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (changelogDoc.patchEntries.isNotEmpty()) {
                    ChangelogPatchCompactBox(
                        title = changelogDoc.patchTitle ?: "🛠️ Neu & behoben im aktuellen Patch",
                        versionBadge = shortPatchBadge,
                        entries = changelogDoc.patchEntries
                    )
                }

                if (changelogDoc.featureEntries.isNotEmpty()) {
                    if (!changelogDoc.featureTitle.isNullOrBlank()) {
                        Text(
                            text = changelogDoc.featureTitle,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = if (changelogDoc.patchEntries.isNotEmpty()) 4.dp else 0.dp, start = 4.dp)
                        )
                    }
                    changelogDoc.featureEntries.forEach { item ->
                        ChangelogCard(item)
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Bottom Dismiss Button
            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = CircleShape
            ) {
                Text(
                    text = stringResource(R.string.changelog_button_dismiss),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
fun ChangelogPatchCompactBox(
    title: String,
    versionBadge: String,
    entries: List<ChangelogEntry>
) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(Modifier.size(8.dp))
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primary
                ) {
                    Text(
                        text = versionBadge,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimary,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            HorizontalDivider(
                thickness = 1.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
            )

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Text(
                            text = entry.icon,
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = buildAnnotatedString {
                                if (entry.title.isNotBlank()) {
                                    withStyle(
                                        SpanStyle(
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    ) {
                                        append(entry.title)
                                        if (entry.description.isNotBlank()) {
                                            append(": ")
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
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChangelogCard(entry: ChangelogEntry) {
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.Top
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = entry.icon,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (entry.title.isNotBlank()) {
                    Text(
                        text = entry.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = entry.description,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
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

internal fun parseChangelogDocument(content: String): ChangelogDocument {
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
                patchTitle = rawHeading
                    .removePrefix("[Patch]")
                    .removePrefix("[patch]")
                    .removePrefix("Patch:")
                    .trim()
            } else {
                currentSection = ChangelogSectionType.FEATURES
                featureTitle = rawHeading
                    .removePrefix("[Features]")
                    .removePrefix("[features]")
                    .removePrefix("Features:")
                    .trim()
            }
        } else if (trimmed.startsWith("- ") || trimmed.startsWith("* ")) {
            val itemText = trimmed.substring(2).trim()
            val match = boldRegex.find(itemText)
            val entry = if (match != null) {
                val icon = match.groupValues[1].ifBlank { "✨" }
                val title = match.groupValues[2].trim()
                val description = match.groupValues[3].trim()
                ChangelogEntry(icon = icon, title = title, description = description)
            } else {
                ChangelogEntry(icon = "•", title = "", description = itemText)
            }
            if (currentSection == ChangelogSectionType.PATCH) {
                patchEntries.add(entry)
            } else {
                featureEntries.add(entry)
            }
        }
    }

    return ChangelogDocument(
        patchTitle = patchTitle,
        patchEntries = patchEntries,
        featureTitle = featureTitle,
        featureEntries = featureEntries
    )
}

internal fun parseChangelogMarkdown(content: String): List<ChangelogEntry> {
    val doc = parseChangelogDocument(content)
    return doc.patchEntries + doc.featureEntries
}
