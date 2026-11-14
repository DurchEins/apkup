package com.apkup.util.play

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import java.util.Locale
import java.util.Properties

/**
 * Privacy-preserving anonymous reference device profile for Google Play token dispenser & FDFE
 * compatibility filtering (`F-07`, `HIGH-11`).
 *
 * Replaces the legacy 32-field hardware/ROM/GPU fingerprint (`Build.FINGERPRINT`, `BOOTLOADER`,
 * `RADIO`, display pixel dimensions, full `systemAvailableFeatures`, `systemSharedLibraryNames`,
 * and `EglExtensionProvider` GPU strings) with a static Pixel 7a (`lynx`) baseline parameterized
 * strictly by:
 * - `Build.SUPPORTED_ABIS` (for correct CPU architecture split selection)
 * - `Build.VERSION.SDK_INT` / `Build.VERSION.RELEASE` (for `minSdkVersion` compatibility)
 * - Active `Locale.getDefault()` (for localized Play metadata, `F-15`)
 */
class NativeDeviceInfoProvider(context: Context) : ContextWrapper(context) {

    companion object {
        private val BASELINE_FEATURES = listOf(
            "android.hardware.audio.output",
            "android.hardware.bluetooth",
            "android.hardware.bluetooth_le",
            "android.hardware.camera",
            "android.hardware.camera.any",
            "android.hardware.camera.autofocus",
            "android.hardware.camera.flash",
            "android.hardware.camera.front",
            "android.hardware.faketouch",
            "android.hardware.fingerprint",
            "android.hardware.location",
            "android.hardware.location.gps",
            "android.hardware.location.network",
            "android.hardware.microphone",
            "android.hardware.nfc",
            "android.hardware.ram.normal",
            "android.hardware.screen.landscape",
            "android.hardware.screen.portrait",
            "android.hardware.sensor.accelerometer",
            "android.hardware.sensor.compass",
            "android.hardware.sensor.gyroscope",
            "android.hardware.sensor.light",
            "android.hardware.sensor.proximity",
            "android.hardware.telephony",
            "android.hardware.telephony.gsm",
            "android.hardware.touchscreen",
            "android.hardware.touchscreen.multitouch",
            "android.hardware.touchscreen.multitouch.distinct",
            "android.hardware.touchscreen.multitouch.jazzhand",
            "android.hardware.usb.accessory",
            "android.hardware.usb.host",
            "android.hardware.vulkan.compute",
            "android.hardware.vulkan.level",
            "android.hardware.vulkan.version",
            "android.hardware.wifi",
            "android.hardware.wifi.direct",
            "android.software.activities_on_secondary_displays",
            "android.software.app_widgets",
            "android.software.autofill",
            "android.software.backup",
            "android.software.companion_device_setup",
            "android.software.connectionservice",
            "android.software.cts",
            "android.software.device_admin",
            "android.software.home_screen",
            "android.software.input_methods",
            "android.software.ManagedUsers",
            "android.software.picture_in_picture",
            "android.software.print",
            "android.software.secure_lock_screen",
            "android.software.voice_recognizers",
            "android.software.webview",
            "com.google.android.feature.GOOGLE_BUILD",
            "com.google.android.feature.GOOGLE_EXPERIENCE"
        )

        private val BASELINE_SHARED_LIBRARIES = listOf(
            "android.ext.shared",
            "android.hidl.base-V1.0-java",
            "android.hidl.manager-V1.0-java",
            "android.net.ipsec.ike",
            "android.test.base",
            "android.test.mock",
            "android.test.runner",
            "androidx.window.extensions",
            "androidx.window.sidecar",
            "com.android.future.usb.accessory",
            "com.android.location.provider",
            "com.android.media.remotedisplay",
            "com.android.mediadrm.signer",
            "com.google.android.gms",
            "javax.obex",
            "org.apache.http.legacy"
        )

        private val BASELINE_GL_EXTENSIONS = listOf(
            "GL_EXT_debug_marker",
            "GL_EXT_robustness",
            "GL_EXT_texture_compression_astc_ldr",
            "GL_EXT_texture_format_BGRA8888",
            "GL_OES_compressed_ETC1_RGB8_texture",
            "GL_OES_depth24",
            "GL_OES_DEPTH_texture",
            "GL_OES_EGL_image",
            "GL_OES_EGL_image_external",
            "GL_OES_packed_depth_stencil",
            "GL_OES_rgb8_rgba8",
            "GL_OES_standard_derivatives",
            "GL_OES_texture_float",
            "GL_OES_texture_half_float",
            "GL_OES_vertex_array_object"
        )

        fun buildAnonymousProperties(
            supportedAbis: List<String> = Build.SUPPORTED_ABIS?.toList().orEmpty().ifEmpty { listOf("arm64-v8a", "armeabi-v7a") },
            sdkInt: Int = Build.VERSION.SDK_INT.takeIf { it > 0 } ?: 35,
            releaseVersion: String = Build.VERSION.RELEASE?.takeIf { it.isNotBlank() } ?: "15",
            activeLocale: Locale = Locale.getDefault()
        ): Properties {
            val localeCode = activeLocale.toLanguageTag().replace('-', '_').ifBlank { "en_US" }
            val localesCsv = listOf(localeCode, "en_US").distinct().joinToString(",")
            return Properties().apply {
                setProperty("UserReadableName", "Pixel 7a-default")
                setProperty("Build.HARDWARE", "lynx")
                setProperty("Build.RADIO", "unknown")
                setProperty("Build.FINGERPRINT", "google/lynx/lynx:$releaseVersion/AP4A.250105.002/12701944:user/release-keys")
                setProperty("Build.BRAND", "google")
                setProperty("Build.DEVICE", "lynx")
                setProperty("Build.VERSION.SDK_INT", "$sdkInt")
                setProperty("Build.VERSION.RELEASE", releaseVersion)
                setProperty("Build.MODEL", "Pixel 7a")
                setProperty("Build.MANUFACTURER", "Google")
                setProperty("Build.PRODUCT", "lynx")
                setProperty("Build.ID", "AP4A.250105.002")
                setProperty("Build.BOOTLOADER", "lynx-1.0")

                // Standardized Pixel 7a screen & input baseline (zero per-device screen pixel fingerprinting)
                setProperty("TouchScreen", "3")
                setProperty("Keyboard", "1")
                setProperty("Navigation", "1")
                setProperty("ScreenLayout", "2")
                setProperty("HasHardKeyboard", "false")
                setProperty("HasFiveWayNavigation", "false")
                setProperty("Screen.Density", "420")
                setProperty("Screen.Width", "1080")
                setProperty("Screen.Height", "2400")

                setProperty("Platforms", supportedAbis.joinToString(","))
                setProperty("Features", BASELINE_FEATURES.joinToString(","))
                setProperty("Locales", localesCsv)
                setProperty("SharedLibraries", BASELINE_SHARED_LIBRARIES.joinToString(","))
                setProperty("GL.Version", "196610")
                setProperty("GL.Extensions", BASELINE_GL_EXTENSIONS.joinToString(","))

                setProperty("Client", "android-google")
                setProperty("GSF.version", "203615037")
                setProperty("Vending.version", "82201710")
                setProperty("Vending.versionString", "22.0.17-21 [0] [PR] 332555730")
                setProperty("Roaming", "mobile-notroaming")
                setProperty("TimeZone", "UTC")
                setProperty("CellOperator", "310")
                setProperty("SimOperator", "38")
            }
        }
    }

    fun getNativeDeviceProperties(): Properties = buildAnonymousProperties()
}
