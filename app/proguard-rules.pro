-keepattributes Signature,InnerClasses,EnclosingMethod,RuntimeVisibleAnnotations,RuntimeVisibleParameterAnnotations,AnnotationDefault
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Gson (R8 Full Mode safe)
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken { *; }
-keep,allowobfuscation,allowshrinking class com.google.gson.Gson
-keep,allowobfuscation,allowshrinking class * implements com.google.gson.TypeAdapterFactory
-keep,allowobfuscation,allowshrinking class * implements com.google.gson.JsonSerializer
-keep,allowobfuscation,allowshrinking class * implements com.google.gson.JsonDeserializer
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# Google Tink
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**

# OkHttp & Retrofit
-keep,allowobfuscation,allowshrinking class okhttp3.RequestBody
-keep,allowobfuscation,allowshrinking class okhttp3.ResponseBody
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Gson-serialized Network, Backup & Persistence DTOs (do not broadly keep Compose UI state models)
-keep class com.apkup.data.apkmirror.** { *; }
-keep class com.apkup.data.fdroid.** { *; }
-keep class com.apkup.data.izzy.** { *; }
-keep class com.apkup.data.obtainium.** { *; }
-keep class com.apkup.data.repomapping.** { *; }
-keep class com.apkup.data.selfupdate.** { *; }
-keep class com.apkup.data.ui.ApkUpBackupConfig { *; }
-keep class com.apkup.data.ui.CachedUpdateSnapshot { *; }
-keep class com.apkup.data.ui.CachedAppUpdateDto { *; }
-keep class com.apkup.data.ui.IgnoredVersionEntry { *; }
-keep class com.apkup.data.ui.UpdateHistoryEntry { *; }
-keep class com.apkup.data.ui.CustomFDroidRepo { *; }
-keep class com.apkup.data.ui.WatchedAppEntry { *; }
-keep class com.apkup.repository.KnownRepoEntry { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }

# Strip verbose, debug, and info logging in release builds (F-11a)
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int) return false;
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Google Play API (Aurora gplayapi) & Protobuf
-keep class com.aurora.gplayapi.** { *; }
-keep interface com.aurora.gplayapi.** { *; }
-dontwarn com.aurora.gplayapi.**

-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# SessionInstaller & InstallPart
-keep class com.apkup.util.SessionInstaller** { *; }
-keep class com.apkup.util.InstallPart { *; }
