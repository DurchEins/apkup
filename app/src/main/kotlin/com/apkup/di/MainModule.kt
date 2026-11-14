package com.apkup.di

import androidx.work.WorkManager
import com.apkup.BuildConfig
import com.apkup.prefs.Prefs
import com.apkup.prefs.PrefsBackend
import com.apkup.prefs.TinkSecurePrefsBackend
import com.apkup.repository.AndroidApkMirrorCloudflareBypass
import com.apkup.repository.ApkMirrorCloudflareBypass
import com.apkup.repository.ApkMirrorRepository
import com.apkup.repository.ApkMirrorResolver
import com.apkup.repository.AppsRepository
import com.apkup.repository.CodebergRepository
import com.apkup.repository.CustomFDroidReposRepository
import com.apkup.repository.GitHubRepository
import com.apkup.repository.GitLabRepository
import com.apkup.repository.IzzyOnDroidRepository
import com.apkup.repository.PlayRepository
import com.apkup.repository.RepoMappingManager
import com.apkup.repository.SelfUpdateRepository
import com.apkup.repository.UpdatesRepository
import com.apkup.service.ApkMirrorAuthInterceptor
import com.apkup.service.ApkMirrorService
import com.apkup.util.AppJournal
import com.apkup.util.Badger
import com.apkup.util.DownloadNotificationManager
import com.apkup.util.Downloader
import com.apkup.util.InstallLog
import com.apkup.util.SessionInstaller
import com.apkup.util.SnackBar
import com.apkup.util.Stringer
import com.apkup.util.Themer
import com.apkup.util.UpdatesNotification
import com.apkup.util.WebKitCookieJar
import com.apkup.util.addUserAgentInterceptor
import com.apkup.util.play.PlayHttpClient
import com.apkup.viewmodel.AppsViewModel
import com.apkup.viewmodel.MainViewModel
import com.apkup.viewmodel.SettingsViewModel
import com.apkup.viewmodel.UpdatesViewModel
import com.apkup.worker.DownloadCoordinator
import com.google.gson.GsonBuilder
import okhttp3.Cache
import okhttp3.Dispatcher
import okhttp3.OkHttpClient
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

val mainModule = module {

	single { GsonBuilder().create() }

	single { Cache(File(androidContext().cacheDir, "http_cache"), 25L * 1024L * 1024L) }

	single<PrefsBackend> { TinkSecurePrefsBackend.create(androidContext()) }

	single { Prefs(get()).apply { migrateLegacyCustomFDroidRepos() } }

	single {
		val dispatcher = Dispatcher().apply {
			maxRequests = 64
			maxRequestsPerHost = 16
		}
		OkHttpClient.Builder()
			.dispatcher(dispatcher)
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(20, TimeUnit.SECONDS)
			.cache(get())
			.addUserAgentInterceptor("ApkUp/${BuildConfig.VERSION_NAME}")
			.build()
	}

	single {
		val apkMirrorApiClient = get<OkHttpClient>().newBuilder()
			.addInterceptor(ApkMirrorAuthInterceptor(get()))
			.build()
		Retrofit.Builder()
			.client(apkMirrorApiClient)
			.baseUrl("https://www.apkmirror.com")
			.addConverterFactory(GsonConverterFactory.create(get()))
			.build()
			.create(ApkMirrorService::class.java)
	}

	single {
		val client = OkHttpClient.Builder()
			.followRedirects(true)
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(45, TimeUnit.SECONDS)
			.writeTimeout(20, TimeUnit.SECONDS)
			.cache(get())
			.addUserAgentInterceptor("ApkUp/${BuildConfig.VERSION_NAME}")
			.build()
		val auroraClient = OkHttpClient.Builder()
			.followRedirects(true)
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(45, TimeUnit.SECONDS)
			.writeTimeout(20, TimeUnit.SECONDS)
			.cache(get())
			.addUserAgentInterceptor("Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36")
			.build()
		Downloader(client, auroraClient)
	}

	single {
		val keylessFallbackClient = get<OkHttpClient>().newBuilder()
			.cookieJar(WebKitCookieJar())
			.build()
		ApkMirrorRepository(get(), androidContext().packageManager, keylessFallbackClient)
	}

	single<ApkMirrorCloudflareBypass> { AndroidApkMirrorCloudflareBypass(androidContext()) }

	single {
		val apkMirrorOkHttpClient = OkHttpClient.Builder()
			.followRedirects(true)
			.cookieJar(WebKitCookieJar())
			.connectTimeout(20, TimeUnit.SECONDS)
			.readTimeout(30, TimeUnit.SECONDS)
			.writeTimeout(20, TimeUnit.SECONDS)
			.build()
		ApkMirrorResolver(
			client = apkMirrorOkHttpClient,
			minDelayMs = 1500L,
			cloudflareBypass = get()
		)
	}

	single { AppsRepository(androidContext(), get()) }

	single { PlayRepository(androidContext(), get(), get(), get()) }

	single { RepoMappingManager(androidContext(), get(), get()) }

	single { IzzyOnDroidRepository(androidContext(), get(), get(), get(), get()) }

	single { GitHubRepository(androidContext(), get(), get(), get(), get()) }

	single { GitLabRepository(androidContext(), get(), get(), get(), get()) }

	single { CodebergRepository(androidContext(), get(), get(), get(), get()) }

	single { CustomFDroidReposRepository(androidContext(), get(), get(), get(), get()) }

	single { SelfUpdateRepository(get(), get()) }

	single {
		UpdatesRepository(
			appsRepository = get(),
			apkMirrorRepository = get(),
			playRepository = get(),
			izzyOnDroidRepository = get(),
			gitHubRepository = get(),
			gitLabRepository = get(),
			prefs = get(),
			repoMappingManager = get(),
			codebergRepository = get(),
			customFDroidReposRepository = get()
		)
	}

	single { UpdatesNotification(androidContext()) }

	single { DownloadNotificationManager(androidContext()) }

	single { SessionInstaller(androidContext(), get(), get(), get(), get()) }

	single { SnackBar() }

	single(createdAtStart = true) { AppJournal.registerShared(AppJournal(androidContext().filesDir)) }

	single { Badger() }

	single { Themer(get()) }

	single { Stringer(androidContext()) }

	single { InstallLog() }

	single { PlayHttpClient(get()) }

	single {
		DownloadCoordinator(
			context = androidContext(),
			downloader = get(),
			installer = get(),
			installLog = get(),
			snackBar = get(),
			stringer = get(),
			prefs = get(),
			apkMirrorResolver = get(),
			repoMappingManager = get(),
			notificationManager = get()
		)
	}

	viewModel {
		MainViewModel(
			prefs = get(),
			workManager = WorkManager.getInstance(androidContext()),
			selfUpdateRepository = get(),
			downloader = get(),
			sessionInstaller = get(),
			snackBar = get()
		)
	}

	viewModel { AppsViewModel(get(), get(), get(), get()) }

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
			updatesNotification = get(),
			downloadCoordinator = get()
		)
	}

	viewModel {
		SettingsViewModel(
			prefs = get(),
			notification = get(),
			workManager = WorkManager.getInstance(androidContext()),
			themer = get(),
			sessionInstaller = get(),
			snackBar = get(),
			stringer = get(),
			gson = get(),
			okHttpClient = get(),
			repoMappingManager = get(),
			appJournal = getOrNull()
		)
	}

}
