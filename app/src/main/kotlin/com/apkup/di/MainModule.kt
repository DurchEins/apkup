package com.apkup.di

import androidx.work.WorkManager
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.prefs.Prefs
import com.apkup.repository.ApkMirrorRepository
import com.apkup.repository.AppsRepository
import com.apkup.repository.PlayRepository
import com.apkup.repository.UpdatesRepository
import com.apkup.service.ApkMirrorService
import com.apkup.util.Badger
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.Themer
import com.apkup.util.UpdatesNotification
import com.apkup.util.addUserAgentInterceptor
import com.apkup.util.play.PlayHttpClient
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.MainViewModel
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import com.google.gson.GsonBuilder
import com.kryptoprefs.preferences.KryptoBuilder
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


val mainModule = module {

	single { GsonBuilder().create() }

	single { Cache(androidContext().cacheDir, 1024 * 1024 * 1024) }

	single {
		val dispatcher = okhttp3.Dispatcher().apply {
			maxRequests = 64
			maxRequestsPerHost = 16
		}
		OkHttpClient.Builder()
			.dispatcher(dispatcher)
			.cache(get())
			.addUserAgentInterceptor("APKUpdater-v" + BuildConfig.VERSION_NAME)
			.build()
	}

	single {
		Retrofit.Builder()
			.client(get())
			.baseUrl("https://www.apkmirror.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(ApkMirrorService::class.java)
	}

	single {
		val client = OkHttpClient.Builder().followRedirects(true).cache(get()).build()
		val auroraClient = OkHttpClient.Builder().followRedirects(true).cache(get()).addUserAgentInterceptor("Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36").build()
		Downloader(client, auroraClient)
	}

	single { ApkMirrorRepository(get(), androidContext().packageManager) }

	single { AppsRepository(get(), get()) }

	single { PlayRepository(get(), get(), get(), get()) }

	single { UpdatesRepository(get(), get(), get(), get()) }

	single { KryptoBuilder.nocrypt(get(), androidContext().getString(R.string.app_name)) }

	single { Prefs(get()) }

	single { UpdatesNotification(get()) }

	single { SessionInstaller(get(), get(), get(), get(), get()) }

	single { SnackBar() }

	single { Badger() }

	single { Themer(get()) }

	single { Stringer(androidContext()) }

	single { InstallLog() }

	single { PlayHttpClient(get()) }

	viewModel { MainViewModel(get(), get(), WorkManager.getInstance(get())) }

	viewModel { AppsViewModel(get(), get(), get()) }

	viewModel { UpdatesViewModel(get(), get(), get(), get(), get(), get(), get(), get(), androidContext()) }

	viewModel { SettingsViewModel(get(), get(), WorkManager.getInstance(get()), get(), get(), get(), get(), get()) }

}
