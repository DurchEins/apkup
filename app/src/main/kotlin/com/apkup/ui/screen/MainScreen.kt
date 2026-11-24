package com.apkup.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import androidx.core.app.NotificationManagerCompat
import androidx.core.util.Consumer
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.data.ui.Screen
import com.apkup.data.ui.UpdatesUiState
import com.apkup.ui.component.ChangelogBottomSheet
import com.apkup.ui.component.InstallPermissionDialog
import com.apkup.ui.component.SelfUpdateDialog
import com.apkup.ui.component.SupportCommunityBottomSheet
import com.apkup.ui.component.SupportLinks
import com.apkup.ui.component.SupportReminderDialog
import com.apkup.ui.theme.ApkUpTheme
import com.apkup.ui.theme.AppTheme
import com.apkup.ui.theme.THEME_ACCESSIBILITY
import com.apkup.util.Badger
import com.apkup.data.snack.SnackVisualType
import com.apkup.data.snack.TextSnack
import com.apkup.util.SnackBar
import com.apkup.util.SolarScheduleCalculator
import com.apkup.util.Themer
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.MainViewModel
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import org.koin.compose.koinInject
import org.koin.compose.viewmodel.koinViewModel
import kotlin.coroutines.CoroutineContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    mainViewModel: MainViewModel = koinViewModel(),
    isColdStart: Boolean = true
) {
    val context = LocalContext.current
    val badger = koinInject<Badger>()
    val themer = koinInject<Themer>()

    val appsViewModel: AppsViewModel = koinViewModel()
    val updatesViewModel: UpdatesViewModel = koinViewModel()
    val settingsViewModel: SettingsViewModel = koinViewModel()

    val showOnboardingConsent by mainViewModel.showOnboardingConsent.collectAsStateWithLifecycle()
    val showPermissionDialog by updatesViewModel.showInstallPermissionDialog.collectAsStateWithLifecycle()
    val updatesState by updatesViewModel.state().collectAsStateWithLifecycle()
    val isCheckingUpdates = !showOnboardingConsent && (
        updatesState is UpdatesUiState.Loading ||
            (updatesState as? UpdatesUiState.Success)?.isChecking == true
        )
    val rootView = LocalView.current
    DisposableEffect(isCheckingUpdates) {
        rootView.keepScreenOn = isCheckingUpdates
        onDispose { rootView.keepScreenOn = false }
    }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START,
                Lifecycle.Event.ON_RESUME -> {
                    themer.refreshSolarTheme()
                    updatesViewModel.setAppInForeground(true)
                    settingsViewModel.refreshShizukuStatus()
                    runCatching {
                        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
                        val isBatteryIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                        val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
                        val shizukuIssue = settingsViewModel.shizukuEnabled().value &&
                            !settingsViewModel.shizukuSetupStatus().value.isBinderRunning
                        val hasWarning = !isBatteryIgnoring || !areNotificationsEnabled || !hasInstallPermission || shizukuIssue
                        badger.changeSettingsBadge(if (hasWarning) "!" else "")
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    updatesViewModel.setAppInForeground(false)
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val navController = rememberNavController()
    val isRefreshing = mainViewModel.isRefreshing.collectAsStateWithLifecycle()
    val pullToRefreshState = rememberPullToRefreshState()
    val currentDestinationRoute = navController.currentBackStackEntryAsState().value?.destination?.route
    val isUpdatesScreen = !shouldShowDefaultPullToRefreshIndicator(currentDestinationRoute)
    val currentVersionCode = BuildConfig.VERSION_CODE.toLong()
    val showChangelogSheet by mainViewModel.showChangelogSheet.collectAsStateWithLifecycle()
    val showSupportPrompt by mainViewModel.showSupportPrompt.collectAsStateWithLifecycle()
    var showSupportCommunitySheet by rememberSaveable { mutableStateOf(false) }
    val selfUpdateState by mainViewModel.selfUpdateState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainViewModel.checkChangelogPrompt(context, currentVersionCode)
        mainViewModel.initializeOnStart(appsViewModel, updatesViewModel, isColdStart)
        mainViewModel.checkSupportPrompt()
    }

    LaunchedEffect(isColdStart, showOnboardingConsent) {
        if (isColdStart && !showOnboardingConsent) {
            mainViewModel.checkAndMarkInstallPermissionPrompt(updatesViewModel)
        }
        if (!showOnboardingConsent) {
            mainViewModel.checkSupportPrompt()
        }
    }

    CheckNotificationIntent(mainViewModel, updatesViewModel, navController)
    IntentListener(mainViewModel, updatesViewModel, navController)

    val theme = themer.flow().collectAsStateWithLifecycle().value
    val dynamicColor = themer.dynamicColorFlow().collectAsStateWithLifecycle().value
    val themeMode = themer.themeModeFlow().collectAsStateWithLifecycle().value
    val highContrast = themer.highContrastFlow().collectAsStateWithLifecycle().value
    val a11ySubMode = themer.a11ySubModeFlow().collectAsStateWithLifecycle().value
    val a11ySunriseMinutes = themer.a11ySunriseMinutesFlow().collectAsStateWithLifecycle().value
    val a11ySunsetMinutes = themer.a11ySunsetMinutesFlow().collectAsStateWithLifecycle().value
    val snackBarHostState = handleSnackBar()

    LaunchedEffect(themeMode, a11ySubMode, a11ySunriseMinutes, a11ySunsetMinutes) {
        themer.refreshSolarTheme()
        while (themeMode == THEME_ACCESSIBILITY && a11ySubMode == SolarScheduleCalculator.SUBMODE_AUTO_SUN) {
            val waitMillis = SolarScheduleCalculator.millisUntilNextTransition(
                customSunriseMinutes = a11ySunriseMinutes,
                customSunsetMinutes = a11ySunsetMinutes
            )
            kotlinx.coroutines.delay(waitMillis)
            themer.refreshSolarTheme()
        }
    }

    AppTheme(darkTheme = theme, dynamicColor = dynamicColor, highContrast = highContrast) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            BoxWithConstraints(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
            ) {
                val useNavigationRail = maxWidth >= 600.dp

                Row(modifier = Modifier.fillMaxSize()) {
                    if (useNavigationRail) {
                        ApkUpNavigationRail(
                            mainViewModel = mainViewModel,
                            updatesViewModel = updatesViewModel,
                            navController = navController
                        )
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    ) {
                        PullToRefreshBox(
                            isRefreshing = isRefreshing.value,
                            onRefresh = { mainViewModel.refresh(appsViewModel, updatesViewModel) },
                            state = pullToRefreshState,
                            indicator = {
                                if (!isUpdatesScreen) {
                                    PullToRefreshDefaults.Indicator(
                                        modifier = Modifier.align(Alignment.TopCenter),
                                        isRefreshing = isRefreshing.value,
                                        state = pullToRefreshState
                                    )
                                }
                            }
                        ) {
                            NavHost(navController, PaddingValues(0.dp), mainViewModel, appsViewModel, updatesViewModel, settingsViewModel)
                        }

                        BackHandler(enabled = currentDestinationRoute != null && currentDestinationRoute != Screen.Updates.route) {
                            mainViewModel.navigateTo(navController, Screen.Updates.route)
                        }

                        ApkUpSnackbarHost(
                            hostState = snackBarHostState,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .navigationBarsPadding()
                                .padding(
                                    bottom = if (useNavigationRail) 20.dp else 94.dp,
                                    start = 20.dp,
                                    end = 20.dp
                                )
                                .widthIn(max = 460.dp)
                        )

                        if (!useNavigationRail) {
                            FloatingCapsuleDock(
                                mainViewModel = mainViewModel,
                                updatesViewModel = updatesViewModel,
                                navController = navController,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .navigationBarsPadding()
                                    .padding(bottom = 12.dp, start = 20.dp, end = 20.dp)
                                    .fillMaxWidth()
                                    .widthIn(max = 420.dp)
                                    .height(68.dp)
                            )
                        }
                    }
                }
            }
        }

        if (showOnboardingConsent) {
            val initial = remember { mainViewModel.getInitialSourceSelection() }
            OnboardingSourceConsentDialog(
                initialPlay = initial.usePlay,
                initialApkMirror = initial.useApkMirror,
                initialIzzy = initial.useIzzyOnDroid,
                initialGitHub = initial.useGitHub,
                initialGitLab = initial.useGitLab,
                onConfirm = { play, apkMirror, izzy, gitHub, gitLab ->
                    mainViewModel.confirmOnboardingConsent(
                        usePlay = play,
                        useApkMirror = apkMirror,
                        useIzzyOnDroid = izzy,
                        useGitHub = gitHub,
                        useGitLab = gitLab,
                        appsViewModel = appsViewModel,
                        updatesViewModel = updatesViewModel
                    )
                    mainViewModel.checkAndMarkInstallPermissionPrompt(updatesViewModel)
                }
            )
        }

        if (showPermissionDialog && !showOnboardingConsent) {
            InstallPermissionDialog(
                onDismiss = { updatesViewModel.dismissInstallPermissionDialog() },
                onOpenSettings = {
                    updatesViewModel.dismissInstallPermissionDialog()
                    runCatching {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                            Uri.parse("package:${context.packageName}")
                        ).apply {
                            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        }
                        context.startActivity(intent)
                    }
                }
            )
        }

        if (showChangelogSheet && !showOnboardingConsent) {
            ChangelogBottomSheet(
                onDismiss = {
                    mainViewModel.dismissChangelogSheet(currentVersionCode)
                    mainViewModel.checkSupportPrompt()
                },
                onOpenSupport = {
                    mainViewModel.dismissChangelogSheet(currentVersionCode)
                    showSupportCommunitySheet = true
                }
            )
        }

        if (showSupportPrompt && !showOnboardingConsent && !showChangelogSheet) {
            val openUrl = com.apkup.ui.component.rememberCustomTabOpener()
            SupportReminderDialog(
                onStarOnGitHub = {
                    mainViewModel.dismissSupportPrompt()
                    openUrl(SupportLinks.GITHUB)
                },
                onOpenSupportSheet = {
                    mainViewModel.dismissSupportPrompt()
                    showSupportCommunitySheet = true
                },
                onSnooze = {
                    mainViewModel.snoozeSupportPrompt()
                },
                onDismissPermanently = {
                    mainViewModel.dismissSupportPrompt()
                }
            )
        }

        if (showSupportCommunitySheet) {
            SupportCommunityBottomSheet(
                onDismiss = { showSupportCommunitySheet = false }
            )
        }

        SelfUpdateDialog(
            state = selfUpdateState,
            onDismiss = { mainViewModel.dismissSelfUpdate() },
            onConfirmUpdate = { info -> mainViewModel.startSelfUpdate(info, context) }
        )
    }
}

@Composable
private fun OnboardingSourceConsentDialog(
    initialPlay: Boolean,
    initialApkMirror: Boolean,
    initialIzzy: Boolean,
    initialGitHub: Boolean,
    initialGitLab: Boolean,
    onConfirm: (Boolean, Boolean, Boolean, Boolean, Boolean) -> Unit
) {
    var usePlay by rememberSaveable { mutableStateOf(initialPlay) }
    var useApkMirror by rememberSaveable { mutableStateOf(initialApkMirror) }
    var useIzzy by rememberSaveable { mutableStateOf(initialIzzy) }
    var useGitHub by rememberSaveable { mutableStateOf(initialGitHub) }
    var useGitLab by rememberSaveable { mutableStateOf(initialGitLab) }

    AlertDialog(
        onDismissRequest = { /* Mandatory consent before first package scan (F-07) */ },
        properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false),
        shape = RoundedCornerShape(28.dp),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        title = {
            Text(
                text = stringResource(R.string.onboarding_consent_title),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = stringResource(R.string.onboarding_consent_desc),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OnboardingSourceToggleRow(
                    iconRes = R.drawable.ic_play,
                    title = stringResource(R.string.source_play),
                    subtitle = stringResource(R.string.onboarding_consent_play_desc),
                    checked = usePlay,
                    onCheckedChange = { usePlay = it }
                )
                OnboardingSourceToggleRow(
                    iconRes = R.drawable.ic_apkmirror,
                    title = stringResource(R.string.source_apkmirror),
                    subtitle = stringResource(R.string.onboarding_consent_apkmirror_desc),
                    checked = useApkMirror,
                    onCheckedChange = { useApkMirror = it }
                )
                OnboardingSourceToggleRow(
                    iconRes = R.drawable.ic_izzy,
                    title = stringResource(R.string.source_izzyondroid),
                    subtitle = stringResource(R.string.onboarding_consent_fdroid_desc),
                    checked = useIzzy,
                    onCheckedChange = { useIzzy = it }
                )
                OnboardingSourceToggleRow(
                    iconRes = R.drawable.ic_github,
                    title = stringResource(R.string.source_github),
                    subtitle = stringResource(R.string.onboarding_consent_git_desc),
                    checked = useGitHub,
                    onCheckedChange = { useGitHub = it }
                )
                OnboardingSourceToggleRow(
                    iconRes = R.drawable.ic_gitlab,
                    title = stringResource(R.string.source_gitlab),
                    subtitle = stringResource(R.string.onboarding_consent_git_desc),
                    checked = useGitLab,
                    onCheckedChange = { useGitLab = it }
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(usePlay, useApkMirror, useIzzy, useGitHub, useGitLab) },
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp),
                shape = CircleShape
            ) {
                Text(
                    text = stringResource(R.string.onboarding_consent_confirm),
                    fontWeight = FontWeight.Bold
                )
            }
        }
    )
}

@Composable
private fun OnboardingSourceToggleRow(
    iconRes: Int,
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .toggleable(
                    value = checked,
                    role = Role.Switch,
                    onValueChange = onCheckedChange
                )
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = painterResource(iconRes),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Switch(
                checked = checked,
                onCheckedChange = null
            )
        }
    }
}

internal fun shouldShowDefaultPullToRefreshIndicator(route: String?): Boolean = route != Screen.Updates.route

@Composable
fun handleSnackBar(): SnackbarHostState {
    val snackBarHostState = remember { SnackbarHostState() }
    koinInject<SnackBar>().flow().CollectAsEffect(Dispatchers.IO) {
        snackBarHostState.showSnackbar(it)
    }
    return snackBarHostState
}

@Composable
fun ApkUpSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier
    ) { snackbarData ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
            tonalElevation = 6.dp,
            shadowElevation = 10.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                val visualType = (snackbarData.visuals as? TextSnack)?.visualType ?: SnackVisualType.INFO
                val extendedColors = ApkUpTheme.colors
                val (badgeBg, badgeTint, badgeIcon) = when (visualType) {
                    SnackVisualType.SUCCESS -> Triple(
                        extendedColors.successContainer,
                        extendedColors.success,
                        Icons.Default.Check
                    )
                    SnackVisualType.WARNING -> Triple(
                        extendedColors.warningContainer,
                        extendedColors.warning,
                        Icons.Default.Warning
                    )
                    SnackVisualType.ERROR -> Triple(
                        MaterialTheme.colorScheme.errorContainer,
                        MaterialTheme.colorScheme.error,
                        Icons.Default.Warning
                    )
                    SnackVisualType.INFO -> Triple(
                        extendedColors.infoContainer,
                        extendedColors.info,
                        Icons.Default.Info
                    )
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = badgeIcon,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Text(
                    text = snackbarData.visuals.message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.weight(1f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis
                )
                val actionLabel = snackbarData.visuals.actionLabel
                if (!actionLabel.isNullOrBlank()) {
                    TextButton(onClick = { snackbarData.performAction() }) {
                        Text(
                            text = actionLabel,
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun <T> Flow<T>.CollectAsEffect(
    context: CoroutineContext = Dispatchers.IO,
    block: suspend (T) -> Unit
) = LaunchedEffect(Unit) {
    onEach(block).flowOn(context).launchIn(this)
}

@Composable
fun IntentListener(
    mainViewModel: MainViewModel,
    updatesViewModel: UpdatesViewModel,
    navController: NavController
) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    DisposableEffect(Unit) {
        val listener = Consumer<Intent> {
            mainViewModel.processIntent(it, updatesViewModel, navController)
        }
        activity.addOnNewIntentListener(listener)
        onDispose { activity.removeOnNewIntentListener(listener) }
    }
}

@Composable
fun CheckNotificationIntent(
    mainViewModel: MainViewModel,
    updatesViewModel: UpdatesViewModel,
    navController: NavController
) {
    val activity = LocalActivity.current as? ComponentActivity ?: return
    LaunchedEffect(Unit) {
        mainViewModel.processIntent(activity.intent, updatesViewModel, navController)
    }
}

@Composable
fun FloatingCapsuleDock(
    mainViewModel: MainViewModel,
    updatesViewModel: UpdatesViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val badges = koinInject<Badger>().flow().collectAsStateWithLifecycle().value
    val updatesState = updatesViewModel.state().collectAsStateWithLifecycle().value
    val isChecking = updatesState.isChecking
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Surface(
        modifier = modifier.height(68.dp),
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        tonalElevation = 4.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            mainViewModel.screens.forEach { screen ->
                val selected = currentRoute == screen.route
                val isLiveChecking = screen == Screen.Updates && isChecking
                CapsuleDockItem(
                    screen = screen,
                    selected = selected,
                    badge = if (screen == Screen.Apps) "" else badges[screen.route].orEmpty(),
                    isLoadingUpdates = isLiveChecking,
                    onClick = { mainViewModel.navigateTo(navController, screen.route) },
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkUpNavigationRail(
    mainViewModel: MainViewModel,
    updatesViewModel: UpdatesViewModel,
    navController: NavController,
    modifier: Modifier = Modifier
) {
    val badges = koinInject<Badger>().flow().collectAsStateWithLifecycle().value
    val updatesState = updatesViewModel.state().collectAsStateWithLifecycle().value
    val isChecking = updatesState.isChecking
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    val haptics = LocalHapticFeedback.current
    val extendedColors = ApkUpTheme.colors

    NavigationRail(
        modifier = modifier
            .fillMaxHeight()
            .navigationBarsPadding(),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Spacer(modifier = Modifier.height(12.dp))
        mainViewModel.screens.forEach { screen ->
            val selected = currentRoute == screen.route
            val badge = if (screen == Screen.Apps) "" else badges[screen.route].orEmpty()
            val isLiveChecking = screen == Screen.Updates && isChecking
            NavigationRailItem(
                selected = selected,
                onClick = {
                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    mainViewModel.navigateTo(navController, screen.route)
                },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant
                ),
                icon = {
                    val isReducedMotion = ApkUpTheme.isReducedMotion
                    val iconScale = remember { Animatable(1f) }
                    LaunchedEffect(selected) {
                        if (selected && !isReducedMotion) {
                            iconScale.snapTo(0.84f)
                            iconScale.animateTo(
                                targetValue = 1f,
                                animationSpec = spring(dampingRatio = 0.45f, stiffness = 560f)
                            )
                        }
                    }
                    NavigationBadgeBox(
                        badge = badge,
                        isLoadingUpdates = isLiveChecking,
                        warningContainer = extendedColors.warning,
                        onWarning = extendedColors.onWarning
                    ) {
                        Icon(
                            imageVector = if (selected) screen.iconSelected else screen.icon,
                            contentDescription = null,
                            modifier = Modifier
                                .size(24.dp)
                                .graphicsLayer {
                                    scaleX = iconScale.value
                                    scaleY = iconScale.value
                                }
                        )
                    }
                },
                label = {
                    Text(
                        text = stringResource(screen.resourceId),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        maxLines = 1,
                        softWrap = false
                    )
                }
            )
        }
    }
}

@Composable
private fun NavigationBadgeBox(
    badge: String,
    isLoadingUpdates: Boolean,
    warningContainer: Color,
    onWarning: Color,
    content: @Composable () -> Unit
) {
    BadgedBox(
        badge = {
            if (badge.isNotEmpty()) {
                val isWarning = badge == "!"
                val displayText = if (isWarning) "!" else formatCapsuleBadge(badge)
                val isReducedMotion = ApkUpTheme.isReducedMotion
                Badge(
                    containerColor = if (isWarning) warningContainer else MaterialTheme.colorScheme.error,
                    contentColor = if (isWarning) onWarning else MaterialTheme.colorScheme.onError
                ) {
                    AnimatedContent(
                        targetState = displayText,
                        transitionSpec = {
                            if (isReducedMotion) {
                                fadeIn(tween(80)) togetherWith fadeOut(tween(80))
                            } else {
                                val prevNum = initialState.trimEnd('+').toIntOrNull()
                                val nextNum = targetState.trimEnd('+').toIntOrNull()
                                val goingUp = prevNum == null || nextNum == null || nextNum >= prevNum
                                (slideInVertically(tween(220)) { if (goingUp) it else -it } + fadeIn(tween(180))) togetherWith
                                    (slideOutVertically(tween(220)) { if (goingUp) -it else it } + fadeOut(tween(140)))
                            }
                        },
                        label = "BadgeOdometer"
                    ) { target ->
                        Text(
                            text = target,
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontSize = if (isWarning) 11.sp else 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        )
                    }
                }
            } else if (isLoadingUpdates) {
                CircularProgressIndicator(
                    modifier = Modifier.size(10.dp),
                    strokeWidth = 1.5.dp,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    ) {
        content()
    }
}

internal fun formatCapsuleBadge(badge: String): String {
    val count = badge.toIntOrNull()
    return if (count != null && count > 99) "99+" else badge
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CapsuleDockItem(
    screen: Screen,
    selected: Boolean,
    badge: String,
    isLoadingUpdates: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val motion = ApkUpTheme.motion
    val extendedColors = ApkUpTheme.colors
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        animationSpec = motion.defaultEffectsSpec(),
        label = "CapsuleIndicatorColor"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 64.dp else 32.dp,
        animationSpec = motion.fastSpatialSpec(),
        label = "CapsuleIndicatorWidth"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "CapsuleIconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = motion.defaultEffectsSpec(),
        label = "CapsuleTextColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .semantics {
                role = Role.Tab
                this.selected = selected
            }
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .height(34.dp)
                .widthIn(min = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(34.dp)
                    .width(indicatorWidth)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            val isReducedMotion = ApkUpTheme.isReducedMotion
            val iconScale = remember { Animatable(1f) }
            LaunchedEffect(selected) {
                if (selected && !isReducedMotion) {
                    iconScale.snapTo(0.84f)
                    iconScale.animateTo(
                        targetValue = 1f,
                        animationSpec = spring(dampingRatio = 0.45f, stiffness = 560f)
                    )
                }
            }
            NavigationBadgeBox(
                badge = badge,
                isLoadingUpdates = isLoadingUpdates,
                warningContainer = extendedColors.warning,
                onWarning = extendedColors.onWarning
            ) {
                Icon(
                    imageVector = if (selected) screen.iconSelected else screen.icon,
                    contentDescription = null,
                    modifier = Modifier
                        .size(24.dp)
                        .graphicsLayer {
                            scaleX = iconScale.value
                            scaleY = iconScale.value
                        },
                    tint = iconColor
                )
            }
        }

        Text(
            text = stringResource(screen.resourceId),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
            color = textColor,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.padding(top = 3.dp)
        )
    }
}

@Composable
fun NavHost(
    navController: NavHostController,
    padding: PaddingValues,
    mainViewModel: MainViewModel,
    appsViewModel: AppsViewModel,
    updatesViewModel: UpdatesViewModel,
    settingsViewModel: SettingsViewModel
) {
    val transitionSpec = ApkUpTheme.motion.fastEffectsSpec<Float>()
    NavHost(
        navController = navController,
        startDestination = Screen.Updates.route,
        modifier = Modifier.padding(padding),
        enterTransition = { fadeIn(animationSpec = transitionSpec) },
        exitTransition = { fadeOut(animationSpec = transitionSpec) },
        popEnterTransition = { fadeIn(animationSpec = transitionSpec) },
        popExitTransition = { fadeOut(animationSpec = transitionSpec) }
    ) {
    composable(Screen.Apps.route) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            AppsScreen(appsViewModel, updatesViewModel)
        }
    }
    composable(Screen.Updates.route) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            UpdatesScreen(updatesViewModel, appsViewModel)
        }
    }
    composable(Screen.Settings.route) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            SettingsScreen(settingsViewModel, appsViewModel, updatesViewModel, mainViewModel)
        }
    }
    }
}
