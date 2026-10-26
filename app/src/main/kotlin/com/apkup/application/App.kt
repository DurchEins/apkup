package com.apkup.application

import android.app.Application
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.apkup.di.mainModule
import com.apkup.util.AppLifecycleTracker
import com.apkup.util.DownloadStorageManager
import com.apkup.worker.DownloadCleanupWorker
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class App : Application(), SingletonImageLoader.Factory, KoinComponent {

	override fun onCreate() {
		super.onCreate()
		registerActivityLifecycleCallbacks(AppLifecycleTracker)

		startKoin {
			androidLogger()
			androidContext(this@App)
			modules(mainModule)
		}

		DownloadStorageManager.cleanExpiredDownloads(this)
		runCatching {
			WorkManager.getInstance(this).let { wm ->
				DownloadCleanupWorker.schedule(wm)
			}
		}
	}

	override fun newImageLoader(context: PlatformContext) = ImageLoader
		.Builder(this)
		.components { add(OkHttpNetworkFetcherFactory(callFactory = { get<OkHttpClient>() })) }
		//.logger(DebugLogger())
		.build()

}
