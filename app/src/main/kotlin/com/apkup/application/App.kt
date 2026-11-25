package com.apkup.application

import android.app.Application
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.apkup.BuildConfig
import com.apkup.di.mainModule
import com.apkup.prefs.Prefs
import com.apkup.util.AppLifecycleTracker
import com.apkup.util.DownloadStorageManager
import com.apkup.util.RollbackBackupManager
import com.apkup.util.SessionInstaller
import com.apkup.util.addUserAgentInterceptor
import com.apkup.worker.DownloadCleanupWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin
import java.io.File
import java.util.concurrent.TimeUnit

class App : Application(), SingletonImageLoader.Factory, KoinComponent {

    private val appStartupScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val imageHttpClient: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(20, TimeUnit.SECONDS)
            .callTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
            .cache(Cache(File(cacheDir, "coil_http_cache"), 20L * 1024L * 1024L))
            .addUserAgentInterceptor("ApkUp/${BuildConfig.VERSION_NAME}")
            .build()
    }

    override fun onCreate() {
        super.onCreate()
        com.apkup.core.android.RustCoreEngineProvider.init()
        registerActivityLifecycleCallbacks(AppLifecycleTracker)

        startKoin {
            if (BuildConfig.DEBUG) {
                androidLogger()
            }
            androidContext(this@App)
            modules(mainModule)
        }

        // Run filesystem & orphaned session cleanup off the main thread to avoid cold-start ANRs (HIGH-18, LOW-01)
        appStartupScope.launch {
            runCatching { DownloadStorageManager.cleanExpiredDownloads(this@App) }
            runCatching {
                val prefs = get<Prefs>()
                get<RollbackBackupManager>().cleanExpiredBackups(
                    maxAgeDays = RollbackBackupManager.normalizeMaxAgeDays(prefs.rollbackMaxAgeDays.get()),
                    maxTotalBytes = RollbackBackupManager.budgetMbToBytes(prefs.rollbackMaxBudgetMb.get())
                )
            }
            runCatching { get<SessionInstaller>().cleanOrphanedSessions() }
            runCatching {
                WorkManager.getInstance(this@App).let { wm ->
                    DownloadCleanupWorker.schedule(wm)
                }
            }
        }
    }

    override fun newImageLoader(context: PlatformContext) = ImageLoader
        .Builder(this)
        .components { add(OkHttpNetworkFetcherFactory(callFactory = { imageHttpClient })) }
        .build()
}
