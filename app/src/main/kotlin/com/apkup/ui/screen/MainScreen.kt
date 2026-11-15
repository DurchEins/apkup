package com.apkup.ui.screen

import android.content.Intent
import android.net.Uri
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
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
import com.apkup.ui.theme.AppTheme
import com.apkup.util.Badger
import com.apkup.data.snack.SnackVisualType
import com.apkup.data.snack.TextSnack
import com.apkup.util.SnackBar
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
                    updatesViewModel.setAppInForeground(true)
                    runCatching {
                        val powerManager = context.getSystemService(android.content.Context.POWER_SERVICE) as? PowerManager
                        val isBatteryIgnoring = powerManager?.isIgnoringBatteryOptimizations(context.packageName) == true
                        val areNotificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()
                        val hasInstallPermission = context.packageManager.canRequestPackageInstalls()
                        val hasWarning = !isBatteryIgnoring || !areNotificationsEnabled || !hasInstallPermission
                        if (hasWarning) {
                            badger.changeSettingsBadge("!")
                        }
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
    val selfUpdateState by mainViewModel.selfUpdateState.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        mainViewModel.checkChangelogPrompt(context, currentVersionCode)
        mainViewModel.initializeOnStart(appsViewModel, updatesViewModel, isColdStart)
    }

    LaunchedEffect(isColdStart, showOnboardingConsent) {
        if (isColdStart && !showOnboardingConsent) {
            mainViewModel.checkAndMarkInstallPermissionPrompt(updatesViewModel)
        }
    }

    CheckNotificationIntent(mainViewModel, updatesViewModel, navController)
    IntentListener(mainViewModel, updatesViewModel, navController)

    val theme = koinInject<Themer>().flow().collectAsStateWithLifecycle().value
    val snackBarHostState = handleSnackBar()

    AppTheme(theme) {
        Scaffold(
            containerColor = MaterialTheme.colorScheme.background
        ) { padding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(top = padding.calculateTopPadding())
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
                        .padding(bottom = 92.dp, start = 20.dp, end = 20.dp)
                        .widthIn(max = 420.dp)
                )

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
                }
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
        onClick = { onCheckedChange(!checked) },
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                onCheckedChange = onCheckedChange
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
            shape = RoundedCornerShape(20.dp),
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
                val isDark = isSystemInDarkTheme()
                val (badgeBg, badgeTint, badgeIcon) = when (visualType) {
                    SnackVisualType.SUCCESS -> Triple(
                        if (isDark) Color(0xFF1B3820) else Color(0xFFE8F5E9),
                        if (isDark) Color(0xFF81C784) else Color(0xFF2E7D32),
                        Icons.Default.Check
                    )
                    SnackVisualType.WARNING -> Triple(
                        MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.tertiary,
                        Icons.Default.Warning
                    )
                    SnackVisualType.ERROR -> Triple(
                        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.error,
                        Icons.Default.Warning
                    )
                    SnackVisualType.INFO -> Triple(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        MaterialTheme.colorScheme.primary,
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
        color = MaterialTheme.colorScheme.surfaceContainer,
        tonalElevation = 6.dp,
        shadowElevation = 8.dp,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
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
    val indicatorColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else Color.Transparent,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "CapsuleIndicatorColor"
    )
    val indicatorWidth by animateDpAsState(
        targetValue = if (selected) 64.dp else 36.dp,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "CapsuleIndicatorWidth"
    )
    val iconColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "CapsuleIconColor"
    )
    val textColor by animateColorAsState(
        targetValue = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 380f),
        label = "CapsuleTextColor"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxHeight()
            .clip(CircleShape)
            .clickable {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                onClick()
            }
            .padding(vertical = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .height(36.dp)
                .widthIn(min = 64.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .height(36.dp)
                    .width(indicatorWidth)
                    .clip(CircleShape)
                    .background(indicatorColor)
            )
            BadgedBox(
                badge = {
                    if (badge.isNotEmpty()) {
                        val isWarning = badge == "!"
                        Badge(
                            containerColor = if (isWarning) Color(0xFFE65100) else MaterialTheme.colorScheme.error,
                            contentColor = Color.White
                        ) {
                            Text(
                                text = if (isWarning) "!" else formatCapsuleBadge(badge),
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontSize = if (isWarning) 11.sp else 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            )
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
                Icon(
                    imageVector = if (selected) screen.iconSelected else screen.icon,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp),
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
            modifier = Modifier.padding(top = 4.dp)
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
) = NavHost(
    navController = navController,
    startDestination = Screen.Updates.route,
    modifier = Modifier.padding(padding),
    enterTransition = { fadeIn(animationSpec = tween(180)) },
    exitTransition = { fadeOut(animationSpec = tween(180)) },
    popEnterTransition = { fadeIn(animationSpec = tween(180)) },
    popExitTransition = { fadeOut(animationSpec = tween(180)) }
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
