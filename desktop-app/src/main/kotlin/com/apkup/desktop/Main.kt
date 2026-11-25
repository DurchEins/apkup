package com.apkup.desktop

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Apps
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Build
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FileOpen
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import com.apkup.shared.desktop.AdbDeviceBridge
import com.apkup.shared.desktop.AdbDeviceInfo
import com.apkup.shared.desktop.DesktopDownloader
import com.apkup.shared.desktop.DesktopPlayAdapter
import com.apkup.shared.desktop.DesktopRepoCatalog
import com.apkup.shared.desktop.LocalApkInspection
import com.apkup.shared.engine.KmpBatchVersionRequest
import com.apkup.shared.engine.KmpTelemetryRegistry
import com.apkup.shared.engine.KmpVersionEngine
import com.apkup.shared.model.KmpInstalledApp
import com.apkup.shared.model.KmpRepoBinding
import com.apkup.shared.model.KmpSourceCheckReport
import com.apkup.shared.model.KmpUpdateCandidate
import com.apkup.shared.network.KmpMultiSourceScanner
import com.apkup.shared.network.createKmpHttpClient
import com.apkup.shared.platform.computeFileSha256Hex
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import java.awt.Desktop
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.DnDConstants
import java.awt.dnd.DropTarget
import java.awt.dnd.DropTargetDropEvent
import java.io.File
import java.net.URI
import javax.swing.JFileChooser
import javax.swing.filechooser.FileNameExtensionFilter

private val KmpDesktopDarkColors = darkColorScheme(
    primary = Color(0xFF9E7BFF),
    onPrimary = Color(0xFF1E005E),
    primaryContainer = Color(0xFF3E1F92),
    onPrimaryContainer = Color(0xFFEADDFF),
    secondary = Color(0xFFCCC2DC),
    tertiary = Color(0xFF6EE7B7),
    background = Color(0xFF111217),
    surface = Color(0xFF111217),
    surfaceContainerLowest = Color(0xFF0B0C10),
    surfaceContainerLow = Color(0xFF171920),
    surfaceContainer = Color(0xFF1F222C),
    surfaceContainerHigh = Color(0xFF282C38),
    outlineVariant = Color(0xFF343947)
)

private val KmpDesktopLightColors = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF2E1065),
    background = Color(0xFFF5F3FA),
    surface = Color(0xFFF5F3FA),
    surfaceContainerLow = Color(0xFFF8F6FC),
    surfaceContainer = Color(0xFFEFECF6),
    surfaceContainerHigh = Color(0xFFE7E3F1),
    outlineVariant = Color(0xFFD4CEE3)
)

private enum class DesktopTab(val label: String) {
    UPDATES("Updates"),
    APPS("Apps"),
    SIDELOAD("Sideload"),
    LAB("Labor & Log")
}

private const val IZZY_FINGERPRINT = "3BF0D6ABFEAE2F401707B6D966BE743BF0EEE49C2561B9BA39073711F628937A"

fun main(args: Array<String>) {
    if ("--headless-verify" in args) {
        runBlocking {
            val devices = AdbDeviceBridge.listDevices()
            println("APKUP_DESKTOP_ADB_DEVICES=${devices.size}")
            devices.forEach { d ->
                println("DEVICE: ${d.displayName} ABIs=${d.abis.joinToString(",")}")
                val apps = AdbDeviceBridge.listInstalledApps(d.serial, includeSystem = false)
                println("INSTALLED_USER_APPS=${apps.size}")
            }
            val batch = KmpVersionEngine.evaluateBatch(
                listOf(
                    KmpBatchVersionRequest("sample1", "1.46.0", "v1.47.0"),
                    KmpBatchVersionRequest("sample2", "2.0.0", "v2.0.0")
                )
            )
            val snap = KmpTelemetryRegistry.snapshot.value
            println("KMP_BATCH_OK=${batch.size} elapsedMicros=${snap.lastVersionBatchMicros}")
        }
        return
    }

    application {
        val windowState = rememberWindowState(width = 1280.dp, height = 840.dp)
        var darkTheme by remember { mutableStateOf(true) }

        Window(
            onCloseRequest = ::exitApplication,
            state = windowState,
            title = "ApkUp KMP Desktop · ADB Companion & Update Hub"
        ) {
            MaterialTheme(colorScheme = if (darkTheme) KmpDesktopDarkColors else KmpDesktopLightColors) {
                DesktopApp(
                    awtWindow = window,
                    darkTheme = darkTheme,
                    onToggleTheme = { darkTheme = !darkTheme }
                )
            }
        }
    }
}

@Composable
private fun DesktopApp(
    awtWindow: java.awt.Window,
    darkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val httpClient = remember { createKmpHttpClient("ApkUp-KMP-Desktop/1.47.0") }
    val scanner = remember(httpClient) { KmpMultiSourceScanner(httpClient) }
    val playAdapter = remember { DesktopPlayAdapter() }

    val telemetry by KmpTelemetryRegistry.snapshot.collectAsState()
    var currentTab by remember { mutableStateOf(DesktopTab.UPDATES) }

    val devices = remember { mutableStateListOf<AdbDeviceInfo>() }
    var selectedDevice by remember { mutableStateOf<AdbDeviceInfo?>(null) }
    var isRefreshingDevices by remember { mutableStateOf(false) }

    val installedApps = remember { mutableStateListOf<KmpInstalledApp>() }
    var includeSystemApps by remember { mutableStateOf(false) }
    var isLoadingApps by remember { mutableStateOf(false) }
    var appSearchQuery by remember { mutableStateOf("") }

    val updates = remember { mutableStateListOf<KmpUpdateCandidate>() }
    val sourceReports = remember { mutableStateListOf<KmpSourceCheckReport>() }
    var selectedUpdate by remember { mutableStateOf<KmpUpdateCandidate?>(null) }
    var selectedApp by remember { mutableStateOf<KmpInstalledApp?>(null) }
    var isScanningUpdates by remember { mutableStateOf(false) }
    var activeInstallPkg by remember { mutableStateOf<String?>(null) }

    var useGitHub by remember { mutableStateOf(true) }
    var useGitLabCodeberg by remember { mutableStateOf(true) }
    var useIzzyOnDroid by remember { mutableStateOf(true) }
    var usePlay by remember { mutableStateOf(true) }
    var useApkMirror by remember { mutableStateOf(false) }
    var githubToken by remember { mutableStateOf("") }

    var showWirelessDialog by remember { mutableStateOf(false) }
    var linkRepoTarget by remember { mutableStateOf<KmpInstalledApp?>(null) }
    val repoBindings = remember { mutableStateOf(DesktopRepoCatalog.loadAllBindings()) }

    val stagedSideloadFiles = remember { mutableStateListOf<LocalApkInspection>() }
    var isInstallingSideload by remember { mutableStateOf(false) }

    val logs = remember { mutableStateListOf<String>() }
    fun log(msg: String) {
        logs.add(0, msg)
        if (logs.size > 150) logs.removeAt(logs.lastIndex)
    }

    // Drag & drop support for .apk files onto Desktop window
    DisposableEffect(awtWindow) {
        val dt = DropTarget(
            awtWindow,
            object : java.awt.dnd.DropTargetAdapter() {
                override fun drop(event: DropTargetDropEvent) {
                    runCatching {
                        event.acceptDrop(DnDConstants.ACTION_COPY)
                        val transferable = event.transferable
                        if (transferable.isDataFlavorSupported(DataFlavor.javaFileListFlavor)) {
                            @Suppress("UNCHECKED_CAST")
                            val files = transferable.getTransferData(DataFlavor.javaFileListFlavor) as List<File>
                            val apks = files.filter { it.isFile && it.extension.equals("apk", ignoreCase = true) }
                            if (apks.isNotEmpty()) {
                                currentTab = DesktopTab.SIDELOAD
                                scope.launch {
                                    stagedSideloadFiles.clear()
                                    apks.forEach { apk ->
                                        val info = AdbDeviceBridge.inspectLocalApk(apk)
                                        stagedSideloadFiles.add(info)
                                        log("APK inspiziert: ${info.fileName} (${info.sha256Hex.take(12)}…, ABIs=${info.abis.joinToString(",")})")
                                    }
                                }
                            }
                        }
                        event.dropComplete(true)
                    }.onFailure { event.dropComplete(false) }
                }
            }
        )
        onDispose { dt.isActive = false }
    }

    fun refreshDevicesAndApps() {
        scope.launch {
            isRefreshingDevices = true
            val found = AdbDeviceBridge.listDevices()
            devices.clear()
            devices.addAll(found)
            val chosen = found.firstOrNull { it.serial == selectedDevice?.serial } ?: found.firstOrNull()
            selectedDevice = chosen
            isRefreshingDevices = false
            log("ADB: ${found.size} Gerät(e) erkannt${chosen?.let { " -> aktiv: ${it.displayName}" } ?: ""}")
            if (chosen != null) {
                isLoadingApps = true
                val loaded = AdbDeviceBridge.listInstalledApps(chosen.serial, includeSystem = includeSystemApps)
                installedApps.clear()
                installedApps.addAll(loaded)
                isLoadingApps = false
                log("Apps vom Gerät geladen: ${loaded.size} Pakete")
            } else {
                installedApps.clear()
            }
        }
    }

    fun runUpdateScan() {
        val dev = selectedDevice ?: return
        if (isScanningUpdates) return
        scope.launch {
            isScanningUpdates = true
            KmpTelemetryRegistry.resetScanCounters()
            val scanStart = System.currentTimeMillis()
            updates.clear()
            sourceReports.clear()
            selectedUpdate = null

            val apps = if (installedApps.isEmpty()) {
                val loaded = AdbDeviceBridge.listInstalledApps(dev.serial, includeSystem = includeSystemApps)
                installedApps.clear()
                installedApps.addAll(loaded)
                loaded
            } else {
                installedApps.toList()
            }

            val bindingsMap = repoBindings.value
            val boundPairs = apps.mapNotNull { app -> bindingsMap[app.packageName]?.let { app to it } }
            val reports = mutableListOf<KmpSourceCheckReport>()

            coroutineScope {
                val jobs = buildList {
                    if (useGitHub) {
                        add(async { scanner.checkGitHub(boundPairs, dev.abis, githubToken) })
                    }
                    if (useGitLabCodeberg) {
                        add(async { scanner.checkGitLabAndCodeberg(boundPairs, dev.abis) })
                    }
                    if (useIzzyOnDroid) {
                        add(
                            async {
                                scanner.checkFDroidRepo(
                                    repoName = "IzzyOnDroid",
                                    repoBaseUrl = "https://apt.izzysoft.de/fdroid/repo/",
                                    expectedCertSha256 = IZZY_FINGERPRINT,
                                    installedApps = apps,
                                    supportedAbis = dev.abis
                                )
                            }
                        )
                    }
                    if (usePlay) {
                        add(async { playAdapter.checkUpdates(apps) })
                    }
                    if (useApkMirror) {
                        add(async { scanner.checkApkMirrorDesktop(apps, dev.abis) })
                    }
                }
                jobs.forEach { reports += it.await() }
            }

            val merged = reports.flatMap { it.updates }
                .groupBy { it.packageName }
                .values
                .map { candidates -> candidates.maxBy { it.newVersionCode } }
                .sortedBy { it.name.lowercase() }

            sourceReports.addAll(reports)
            updates.addAll(merged)
            selectedUpdate = merged.firstOrNull()
            val totalMs = (System.currentTimeMillis() - scanStart).coerceAtLeast(0L)
            KmpTelemetryRegistry.recordScanDuration(totalMs)
            isScanningUpdates = false
            log("Update-Scan abgeschlossen in $totalMs ms: ${merged.size} Update(s) über ${reports.size} Quelle(n)")
        }
    }

    LaunchedEffect(Unit) {
        refreshDevicesAndApps()
    }

    Scaffold(
        topBar = {
            Surface(
                color = MaterialTheme.colorScheme.surfaceContainerLow,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Bolt,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(8.dp).size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "ApkUp KMP Desktop",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = telemetry.engineLabel,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    // Connected Devices Bar
                    Row(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (devices.isEmpty()) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.errorContainer
                            ) {
                                Text(
                                    text = "Kein ADB-Gerät verbunden",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                )
                            }
                        } else {
                            devices.forEach { dev ->
                                FilterChip(
                                    selected = selectedDevice?.serial == dev.serial,
                                    onClick = {
                                        selectedDevice = dev
                                        scope.launch {
                                            isLoadingApps = true
                                            val loaded = AdbDeviceBridge.listInstalledApps(dev.serial, includeSystemApps)
                                            installedApps.clear()
                                            installedApps.addAll(loaded)
                                            isLoadingApps = false
                                        }
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = if (dev.isWireless) Icons.Rounded.Wifi else Icons.Rounded.PhoneAndroid,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    },
                                    label = { Text(dev.displayName, maxLines = 1) }
                                )
                            }
                        }
                    }

                    FilledTonalButton(
                        onClick = { showWirelessDialog = true },
                        shape = CircleShape
                    ) {
                        Icon(Icons.Rounded.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Wireless ADB")
                    }

                    IconButton(onClick = { refreshDevicesAndApps() }, enabled = !isRefreshingDevices) {
                        Icon(Icons.Rounded.Refresh, contentDescription = "Geräte aktualisieren")
                    }

                    OutlinedButton(onClick = onToggleTheme, shape = CircleShape) {
                        Text(if (darkTheme) "Hell" else "Dunkel")
                    }
                }
            }
        }
    ) { innerPadding ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            NavigationRail(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ) {
                Spacer(Modifier.height(12.dp))
                NavigationRailItem(
                    selected = currentTab == DesktopTab.UPDATES,
                    onClick = { currentTab = DesktopTab.UPDATES },
                    icon = { Icon(Icons.Rounded.Download, contentDescription = null) },
                    label = { Text("Updates (${updates.size})") }
                )
                NavigationRailItem(
                    selected = currentTab == DesktopTab.APPS,
                    onClick = { currentTab = DesktopTab.APPS },
                    icon = { Icon(Icons.Rounded.Apps, contentDescription = null) },
                    label = { Text("Apps (${installedApps.size})") }
                )
                NavigationRailItem(
                    selected = currentTab == DesktopTab.SIDELOAD,
                    onClick = { currentTab = DesktopTab.SIDELOAD },
                    icon = { Icon(Icons.Rounded.UploadFile, contentDescription = null) },
                    label = { Text("Sideload") }
                )
                NavigationRailItem(
                    selected = currentTab == DesktopTab.LAB,
                    onClick = { currentTab = DesktopTab.LAB },
                    icon = { Icon(Icons.Rounded.Build, contentDescription = null) },
                    label = { Text("Labor & Log") }
                )
            }

            // Main Split-Pane Content
            Box(modifier = Modifier.weight(1f).fillMaxHeight().padding(16.dp)) {
                when (currentTab) {
                    DesktopTab.UPDATES -> {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            // Left: Update list
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Column {
                                            Text("Verfügbare Updates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                            Text(
                                                "Letzter Scan: ${telemetry.lastScanDurationMs} ms · Batch: ${telemetry.lastVersionBatchMicros} µs",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Button(
                                            onClick = { runUpdateScan() },
                                            enabled = selectedDevice != null && !isScanningUpdates,
                                            shape = CircleShape
                                        ) {
                                            if (isScanningUpdates) {
                                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                                Spacer(Modifier.width(8.dp))
                                            }
                                            Text(if (isScanningUpdates) "Prüfe…" else "Updates suchen")
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    if (sourceReports.isNotEmpty()) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            sourceReports.forEach { rep ->
                                                Surface(
                                                    shape = CircleShape,
                                                    color = if (rep.errorMessage != null && rep.updates.isEmpty()) {
                                                        MaterialTheme.colorScheme.errorContainer
                                                    } else {
                                                        MaterialTheme.colorScheme.surfaceContainerHigh
                                                    }
                                                ) {
                                                    Text(
                                                        "${rep.sourceName}: ${rep.updates.size} (${rep.durationMs}ms)",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                                    )
                                                }
                                            }
                                        }
                                        Spacer(Modifier.height(10.dp))
                                    }
                                    if (updates.isEmpty()) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            Text(
                                                text = if (isScanningUpdates) "Suche läuft über :shared Ktor-Quellen…" else "Klicke auf „Updates suchen“, um die Apps auf dem verbundenen Pixel zu prüfen.",
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            items(updates, key = { "${it.sourceName}_${it.packageName}_${it.newVersionCode}" }) { upd ->
                                                val isSelected = selectedUpdate?.packageName == upd.packageName && selectedUpdate?.sourceName == upd.sourceName
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(14.dp))
                                                        .clickable { selectedUpdate = upd },
                                                    shape = RoundedCornerShape(14.dp),
                                                    color = if (isSelected) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f) else MaterialTheme.colorScheme.surfaceContainer,
                                                    border = BorderStroke(
                                                        1.dp,
                                                        if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                                                    )
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(upd.name, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                                            Text(
                                                                "${upd.packageName} · ${upd.sourceName}",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                "${upd.currentVersion} → ${upd.newVersion}",
                                                                style = MaterialTheme.typography.labelMedium,
                                                                color = MaterialTheme.colorScheme.primary
                                                            )
                                                        }
                                                        if (activeInstallPkg == upd.packageName) {
                                                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            // Right: Detail Inspector Pane
                            Surface(
                                modifier = Modifier.width(440.dp).fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                val detail = selectedUpdate
                                if (detail == null) {
                                    Box(modifier = Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                        Text("Update auswählen, um Release Notes, SHA-256 & ADB-Installation zu sehen.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    Column(
                                        modifier = Modifier.fillMaxSize().padding(16.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        Text(detail.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(detail.packageName, style = MaterialTheme.typography.labelMedium, fontFamily = FontFamily.Monospace)
                                        Surface(
                                            shape = RoundedCornerShape(10.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainerHigh
                                        ) {
                                            Column(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text("Quelle: ${detail.sourceName}")
                                                Text("Version: ${detail.currentVersion} (${detail.currentVersionCode}) → ${detail.newVersion} (${detail.newVersionCode})")
                                                Text("SHA-256: ${detail.expectedSha256 ?: "nicht angegeben"}")
                                            }
                                        }
                                        Text("Versionshinweise", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold)
                                        Surface(
                                            modifier = Modifier.weight(1f).fillMaxWidth(),
                                            shape = RoundedCornerShape(12.dp),
                                            color = MaterialTheme.colorScheme.surfaceContainer
                                        ) {
                                            Text(
                                                text = detail.releaseNotes.ifBlank { "Keine Release Notes hinterlegt." },
                                                style = MaterialTheme.typography.bodySmall,
                                                modifier = Modifier.padding(12.dp).verticalScroll(rememberScrollState())
                                            )
                                        }
                                        Button(
                                            onClick = {
                                                val dev = selectedDevice ?: return@Button
                                                if (detail.requiresBrowserFallback) {
                                                    runCatching { Desktop.getDesktop().browse(URI(detail.downloadUrl)) }
                                                    log("Browser geöffnet für APKMirror: ${detail.downloadUrl}")
                                                    return@Button
                                                }
                                                scope.launch {
                                                    activeInstallPkg = detail.packageName
                                                    try {
                                                        val tmpDir = File(System.getProperty("java.io.tmpdir"), "apkup-desktop-dl/${detail.packageName}")
                                                        tmpDir.deleteRecursively()
                                                        val downloadedFiles = if (detail.sourceName == "Google Play") {
                                                            log("Lade Play-Dateien für ${detail.packageName}…")
                                                            val urls = playAdapter.resolvePlayApkUrls(detail.packageName, detail.newVersionCode)
                                                            urls.map { (name, url) ->
                                                                DesktopDownloader.downloadAndVerifyApk(httpClient, url, File(tmpDir, name), null)
                                                            }
                                                        } else {
                                                            log("Lade ${detail.newVersion} von ${detail.sourceName}…")
                                                            val f = DesktopDownloader.downloadAndVerifyApk(
                                                                httpClient,
                                                                detail.downloadUrl,
                                                                File(tmpDir, "${detail.packageName}-${detail.newVersion}.apk"),
                                                                detail.expectedSha256
                                                            )
                                                            listOf(f)
                                                        }
                                                        log("Installiere ${downloadedFiles.size} APK(s) per ADB auf ${dev.model}…")
                                                        val res = AdbDeviceBridge.installApks(dev.serial, downloadedFiles)
                                                        log(if (res.isSuccess) "ADB Install OK: ${detail.packageName}" else "ADB Install Fehler: ${res.combinedOutput}")
                                                    } catch (t: Throwable) {
                                                        log("Fehler bei Installation: ${t.message}")
                                                    } finally {
                                                        activeInstallPkg = null
                                                    }
                                                }
                                            },
                                            enabled = selectedDevice != null && activeInstallPkg == null,
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Rounded.Download, contentDescription = null, modifier = Modifier.size(18.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                if (detail.requiresBrowserFallback) "Im Windows-Browser öffnen"
                                                else "Download & per ADB installieren"
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DesktopTab.APPS -> {
                        val filteredApps = installedApps.filter {
                            appSearchQuery.isBlank() ||
                                it.packageName.contains(appSearchQuery, ignoreCase = true) ||
                                it.name.contains(appSearchQuery, ignoreCase = true)
                        }
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = appSearchQuery,
                                            onValueChange = { appSearchQuery = it },
                                            placeholder = { Text("App oder Paket-ID suchen…") },
                                            leadingIcon = { Icon(Icons.Rounded.Search, contentDescription = null) },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Checkbox(
                                                checked = includeSystemApps,
                                                onCheckedChange = {
                                                    includeSystemApps = it
                                                    refreshDevicesAndApps()
                                                }
                                            )
                                            Text("System-Apps")
                                        }
                                    }
                                    Spacer(Modifier.height(10.dp))
                                    if (isLoadingApps) {
                                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                            CircularProgressIndicator()
                                        }
                                    } else {
                                        LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                            items(filteredApps, key = { it.packageName }) { app ->
                                                val hasRepo = repoBindings.value.containsKey(app.packageName)
                                                Surface(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clip(RoundedCornerShape(12.dp))
                                                        .clickable { selectedApp = app },
                                                    shape = RoundedCornerShape(12.dp),
                                                    color = if (selectedApp?.packageName == app.packageName) {
                                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                                                    } else MaterialTheme.colorScheme.surfaceContainer
                                                ) {
                                                    Row(
                                                        modifier = Modifier.padding(12.dp),
                                                        verticalAlignment = Alignment.CenterVertically,
                                                        horizontalArrangement = Arrangement.SpaceBetween
                                                    ) {
                                                        Column(modifier = Modifier.weight(1f)) {
                                                            Text(app.name, fontWeight = FontWeight.SemiBold)
                                                            Text(
                                                                "${app.packageName} · v${app.version} (${app.versionCode})",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                        }
                                                        if (hasRepo) {
                                                            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primaryContainer) {
                                                                Text(
                                                                    repoBindings.value[app.packageName]?.type?.uppercase() ?: "REPO",
                                                                    style = MaterialTheme.typography.labelSmall,
                                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                                                )
                                                            }
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.width(400.dp).fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                val app = selectedApp
                                if (app == null) {
                                    Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
                                        Text("App auswählen für ADB APK-Backup oder Repo-Verknüpfung.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                } else {
                                    val binding = repoBindings.value[app.packageName]
                                    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                        Text(app.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                                        Text(app.packageName, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelMedium)
                                        Text("Version: ${app.version} (${app.versionCode})")
                                        Text("Gerätepfad: ${app.apkPathOnDevice.ifBlank { "unbekannt" }}", style = MaterialTheme.typography.labelSmall)
                                        Text("Repo-Binding: ${binding?.webUrl ?: "nicht verknüpft"}")
                                        Spacer(Modifier.weight(1f))
                                        FilledTonalButton(
                                            onClick = { linkRepoTarget = app },
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Rounded.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("Repository verknüpfen")
                                        }
                                        Button(
                                            onClick = {
                                                val dev = selectedDevice ?: return@Button
                                                if (app.apkPathOnDevice.isBlank()) return@Button
                                                val chooser = JFileChooser().apply {
                                                    selectedFile = File("${app.packageName}-${app.version}.apk")
                                                }
                                                if (chooser.showSaveDialog(awtWindow) == JFileChooser.APPROVE_OPTION) {
                                                    val dest = chooser.selectedFile
                                                    scope.launch {
                                                        val res = AdbDeviceBridge.pullApk(dev.serial, app.apkPathOnDevice, dest)
                                                        log(if (res.isSuccess) "APK gesichert: ${dest.absolutePath}" else "ADB Pull Fehler: ${res.combinedOutput}")
                                                    }
                                                }
                                            },
                                            enabled = selectedDevice != null && app.apkPathOnDevice.isNotBlank(),
                                            modifier = Modifier.fillMaxWidth(),
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Rounded.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(8.dp))
                                            Text("APK auf PC sichern (ADB Pull)")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    DesktopTab.SIDELOAD -> {
                        Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(20.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("APK Sideload & Inspektor (Drag & Drop)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Ziehe eine oder mehrere .apk-Dateien direkt in dieses Fenster oder wähle sie per Dateidialog aus.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                        FilledTonalButton(
                                            onClick = {
                                                val chooser = JFileChooser().apply {
                                                    isMultiSelectionEnabled = true
                                                    fileFilter = FileNameExtensionFilter("Android APK", "apk")
                                                }
                                                if (chooser.showOpenDialog(awtWindow) == JFileChooser.APPROVE_OPTION) {
                                                    val files = chooser.selectedFiles.toList().ifEmpty { listOfNotNull(chooser.selectedFile) }
                                                    scope.launch {
                                                        stagedSideloadFiles.clear()
                                                        files.forEach { f ->
                                                            stagedSideloadFiles.add(AdbDeviceBridge.inspectLocalApk(f))
                                                        }
                                                    }
                                                }
                                            },
                                            shape = CircleShape
                                        ) {
                                            Icon(Icons.Rounded.FileOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(Modifier.width(6.dp))
                                            Text("APK(s) wählen")
                                        }
                                        Button(
                                            onClick = {
                                                val dev = selectedDevice ?: return@Button
                                                scope.launch {
                                                    isInstallingSideload = true
                                                    val files = stagedSideloadFiles.map { File(it.filePath) }
                                                    val res = AdbDeviceBridge.installApks(dev.serial, files)
                                                    log(if (res.isSuccess) "Sideload erfolgreich auf ${dev.model}!" else "Sideload Fehler: ${res.combinedOutput}")
                                                    isInstallingSideload = false
                                                }
                                            },
                                            enabled = selectedDevice != null && stagedSideloadFiles.isNotEmpty() && !isInstallingSideload,
                                            shape = CircleShape
                                        ) {
                                            Text(if (isInstallingSideload) "Installiere…" else "Per ADB installieren")
                                        }
                                    }
                                }
                            }

                            LazyColumn(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                items(stagedSideloadFiles, key = { it.filePath }) { apk ->
                                    Surface(
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(14.dp),
                                        color = MaterialTheme.colorScheme.surfaceContainerLow,
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                Icon(
                                                    if (apk.hasAndroidManifest) Icons.Rounded.CheckCircle else Icons.Rounded.Security,
                                                    contentDescription = null,
                                                    tint = MaterialTheme.colorScheme.primary
                                                )
                                                Text(apk.fileName, fontWeight = FontWeight.Bold)
                                                Text("(${apk.sizeBytes / (1024 * 1024)} MB)", style = MaterialTheme.typography.labelSmall)
                                            }
                                            Text("SHA-256: ${apk.sha256Hex} (${apk.sha256Micros / 1000.0} ms)", fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                            Text("ABIs: ${apk.abis.joinToString(", ")} · Manifest: ${if (apk.hasAndroidManifest) "OK" else "Fehlt"}", style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("Shizuku-Dienst per ADB auf dem verbundenen Gerät starten")
                                    OutlinedButton(
                                        onClick = {
                                            val dev = selectedDevice ?: return@OutlinedButton
                                            scope.launch {
                                                val res = AdbDeviceBridge.startShizukuViaAdb(dev.serial)
                                                log("Shizuku ADB Start: ${res.combinedOutput.ifBlank { "Exit ${res.exitCode}" }}")
                                            }
                                        },
                                        enabled = selectedDevice != null,
                                        shape = CircleShape
                                    ) {
                                        Text("Shizuku starten")
                                    }
                                }
                            }
                        }
                    }

                    DesktopTab.LAB -> {
                        Row(modifier = Modifier.fillMaxSize(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Surface(
                                modifier = Modifier.width(420.dp).fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                    Text("Update-Quellen & KMP Benchmark", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    SourceToggleRow("GitHub Releases (Ktor)", useGitHub) { useGitHub = it }
                                    SourceToggleRow("GitLab & Codeberg (Ktor)", useGitLabCodeberg) { useGitLabCodeberg = it }
                                    SourceToggleRow("IzzyOnDroid / F-Droid (Signed JAR)", useIzzyOnDroid) { useIzzyOnDroid = it }
                                    SourceToggleRow("Google Play (aurora-gplayapi JVM)", usePlay) { usePlay = it }
                                    SourceToggleRow("APKMirror (Ktor + Browser-Fallback)", useApkMirror) { useApkMirror = it }
                                    OutlinedTextField(
                                        value = githubToken,
                                        onValueChange = { githubToken = it },
                                        label = { Text("GitHub Token (optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    HorizontalDivider()
                                    Text("Engine-Telemetrie", fontWeight = FontWeight.Bold)
                                    Text("Scan: ${telemetry.lastScanDurationMs} ms · Batch: ${telemetry.lastVersionBatchMicros} µs (${telemetry.lastVersionBatchCount})")
                                    Text("F-Droid JAR: ${String.format("%.2f", telemetry.lastFDroidJarVerifyMs)} ms · Parse: ${String.format("%.2f", telemetry.lastFDroidParseMs)} ms")
                                    Text("SHA-256: ${String.format("%.2f", telemetry.lastSha256DurationMs)} ms (${String.format("%.1f", telemetry.lastSha256ThroughputMbps)} MB/s)")
                                    Button(
                                        onClick = {
                                            scope.launch(Dispatchers.IO) {
                                                val sample = File("ApkUp-Rust-Lab.apk").takeIf { it.isFile }
                                                    ?: File("ApkUp-KMP-Lab.apk").takeIf { it.isFile }
                                                    ?: File("README.md")
                                                if (sample.isFile) {
                                                    val res = computeFileSha256Hex(sample.absolutePath)
                                                    log("KMP Hash Benchmark (${sample.name}): ${res.elapsedMicros / 1000.0} ms -> ${res.sha256Hex.take(16)}…")
                                                }
                                            }
                                        },
                                        shape = CircleShape,
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("KMP SHA-256 Benchmark starten")
                                    }
                                }
                            }

                            Surface(
                                modifier = Modifier.weight(1f).fillMaxHeight(),
                                shape = RoundedCornerShape(18.dp),
                                color = MaterialTheme.colorScheme.surfaceContainerLow,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.fillMaxSize().padding(14.dp)) {
                                    Text("Live ADB & Netzwerk-Protokoll", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                                    Spacer(Modifier.height(8.dp))
                                    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        items(logs) { entry ->
                                            Text(entry, fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.labelSmall)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (showWirelessDialog) {
            var pairIpPort by remember { mutableStateOf("") }
            var pairCode by remember { mutableStateOf("") }
            var connectIpPort by remember { mutableStateOf("") }
            var statusMsg by remember { mutableStateOf("") }
            AlertDialog(
                onDismissRequest = { showWirelessDialog = false },
                title = { Text("Wireless ADB koppeln & verbinden") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            "Kein Server nötig: Aktiviere auf dem Pixel unter Entwickleroptionen „Kabelloses Debugging“.",
                            style = MaterialTheme.typography.bodySmall
                        )
                        OutlinedTextField(
                            value = pairIpPort,
                            onValueChange = { pairIpPort = it },
                            label = { Text("Pairing IP:Port (z.B. 192.168.1.42:37123)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = pairCode,
                            onValueChange = { pairCode = it },
                            label = { Text("6-stelliger Pairing-Code (optional)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = connectIpPort,
                            onValueChange = { connectIpPort = it },
                            label = { Text("Connect IP:Port (z.B. 192.168.1.42:5555)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (statusMsg.isNotBlank()) {
                            Text(statusMsg, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            scope.launch {
                                if (pairIpPort.isNotBlank() && pairCode.isNotBlank()) {
                                    val pr = AdbDeviceBridge.pairWireless(pairIpPort, pairCode)
                                    statusMsg = pr.combinedOutput
                                    log("ADB Pair: ${pr.combinedOutput}")
                                }
                                if (connectIpPort.isNotBlank()) {
                                    val cr = AdbDeviceBridge.connectWireless(connectIpPort)
                                    statusMsg = cr.combinedOutput
                                    log("ADB Connect: ${cr.combinedOutput}")
                                    refreshDevicesAndApps()
                                }
                            }
                        }
                    ) {
                        Text("Koppeln / Verbinden")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showWirelessDialog = false }) { Text("Schließen") }
                }
            )
        }

        linkRepoTarget?.let { app ->
            var type by remember { mutableStateOf(repoBindings.value[app.packageName]?.type ?: "github") }
            var repo by remember { mutableStateOf(repoBindings.value[app.packageName]?.repo ?: "") }
            var host by remember { mutableStateOf(repoBindings.value[app.packageName]?.host.orEmpty()) }
            AlertDialog(
                onDismissRequest = { linkRepoTarget = null },
                title = { Text("Repo verknüpfen: ${app.name}") },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("github", "gitlab", "codeberg").forEach { t ->
                                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                            }
                        }
                        OutlinedTextField(value = repo, onValueChange = { repo = it }, label = { Text("Owner/Repo") }, singleLine = true)
                        OutlinedTextField(value = host, onValueChange = { host = it }, label = { Text("Custom Host (optional)") }, singleLine = true)
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (repo.isNotBlank()) {
                                DesktopRepoCatalog.saveCustomBinding(
                                    app.packageName,
                                    KmpRepoBinding(type = type, repo = repo.trim(), host = host.trim().ifBlank { null })
                                )
                                repoBindings.value = DesktopRepoCatalog.loadAllBindings()
                                log("Binding gespeichert: ${app.packageName} -> $type:$repo")
                            }
                            linkRepoTarget = null
                        }
                    ) { Text("Speichern") }
                },
                dismissButton = {
                    TextButton(onClick = { linkRepoTarget = null }) { Text("Abbrechen") }
                }
            )
        }
    }
}

@Composable
private fun SourceToggleRow(label: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
