package com.apkup.di

import android.content.Context
import androidx.work.WorkManager
import com.apkup.BuildConfig
import com.apkup.R
import com.apkup.prefs.Prefs
import com.apkup.repository.AndroidApkMirrorCloudflareBypass
import com.apkup.repository.ApkMirrorCloudflareBypass
import com.apkup.repository.ApkMirrorRepository
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.InMemoryCookieJar
import com.apkup.repository.AppsRepository
import com.apkup.repository.GitHubRepository
import com.apkup.repository.GitLabRepository
import com.apkup.repository.IzzyOnDroidRepository
import com.apkup.repository.PlayRepository
import com.apkup.repository.RepoMappingManager
import com.apkup.repository.SelfUpdateRepository
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
import com.kryptoprefs.preferences.KryptoPrefs
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory


val mainModule = module {

	single { GsonBuilder().create() }

	single { Cache(java.io.File(androidContext().cacheDir, "http_cache"), 25L * 1024L * 1024L) }

	single {
		val dispatcher = okhttp3.Dispatcher().apply {
			maxRequests = 64
			maxRequestsPerHost = 16
		}
		OkHttpClient.Builder()
			.dispatcher(dispatcher)
			.connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
			.readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
			.writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
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

	single<ApkMirrorCloudflareBypass> { AndroidApkMirrorCloudflareBypass(androidContext()) }

	single {
		val apkMirrorOkHttpClient = OkHttpClient.Builder()
			.followRedirects(true)
			.cookieJar(com.apkup.util.WebKitCookieJar())
			.connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
			.readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
			.writeTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
			.build()
		ApkMirrorResolver(
			client = apkMirrorOkHttpClient,
			minDelayMs = 1500L,
			cloudflareBypass = get()
		)
	}

	single { AppsRepository(get(), get()) }

	single { PlayRepository(get(), get(), get(), get()) }

	single<KryptoPrefs> { provideSecureKryptoPrefs(get()) }

	single { Prefs(get()) }

	single { RepoMappingManager(androidContext(), get(), get()) }

	single { IzzyOnDroidRepository(androidContext(), get(), get(), get(), get()) }

	single { GitHubRepository(androidContext(), get(), get(), get(), get()) }

	single { GitLabRepository(androidContext(), get(), get(), get(), get()) }

	single { SelfUpdateRepository(get(), get()) }

	single { UpdatesRepository(get(), get(), get(), get(), get(), get(), get()) }

	single { UpdatesNotification(get()) }

	single { SessionInstaller(get(), get(), get(), get(), get()) }

	single { SnackBar() }

	single { Badger() }

	single { Themer(get()) }

	single { Stringer(androidContext()) }

	single { InstallLog() }

	single { PlayHttpClient(get()) }

	viewModel { MainViewModel(get(), get(), WorkManager.getInstance(get()), get(), get(), get(), get()) }

	viewModel { AppsViewModel(get(), get(), get()) }

	viewModel {
		UpdatesViewModel(
			updatesRepository = get(),
			installer = get(),
			prefs = get(),
			badger = get(),
			downloader = get(),
			snackBar = get(),
			stringer = get(),
			installLog = get(),
			context = androidContext(),
			apkMirrorResolver = get(),
			updatesNotification = get()
		)
	}

	viewModel { SettingsViewModel(get(), get(), WorkManager.getInstance(get()), get(), get(), get(), get(), get(), get(), getOrNull()) }

}

private fun provideSecureKryptoPrefs(context: Context): KryptoPrefs {
	return runCatching {
		val rawPrefs = context.getSharedPreferences("ApkUp", Context.MODE_PRIVATE)
		val rawAll = rawPrefs.all
		// KryptoPrefs hybrid uses Sha512Hash for keys (length >= 60). Unencrypted legacy keys are short human-readable strings.
		val legacyKeys = rawAll.filterKeys { key -> key.length < 60 }

		val hybridPrefs = KryptoBuilder.hybrid(context, "ApkUp")
		if (legacyKeys.isNotEmpty()) {
			val editor = rawPrefs.edit()
			for ((key, value) in legacyKeys) {
				when (value) {
					is String -> hybridPrefs.putString(key, value)
					is Boolean -> hybridPrefs.putBoolean(key, value)
					is Int -> hybridPrefs.putInt(key, value)
					is Long -> hybridPrefs.putLong(key, value)
				}
				editor.remove(key)
			}
			editor.apply()
		}
		hybridPrefs
	}.getOrElse {
		KryptoBuilder.nocrypt(context, "ApkUp")
	}
}
