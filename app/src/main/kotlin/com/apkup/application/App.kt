package com.apkup.application

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import com.apkup.di.mainModule
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.component.KoinComponent
import org.koin.core.component.get
import org.koin.core.context.startKoin

class App : Application(), SingletonImageLoader.Factory, KoinComponent {

	override fun onCreate() {
		super.onCreate()
		registerActivityLifecycleCallbacks(com.apkup.util.AppLifecycleTracker)
		com.apkup.util.Downloader(okhttp3.OkHttpClient(), okhttp3.OkHttpClient()).cleanupOrphanStagingFiles(cacheDir)

		startKoin {
			androidLogger()
			androidContext(this@App)
			modules(mainModule)
		}
	}

	override fun newImageLoader(context: PlatformContext) = ImageLoader
		.Builder(this)
		.components { add(OkHttpNetworkFetcherFactory(callFactory = { get<OkHttpClient>() })) }
		//.logger(DebugLogger())
		.build()

}
