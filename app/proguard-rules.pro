-keepattributes Signature
-renamesourcefileattribute SourceFile
-keepattributes SourceFile,LineNumberTable

# Gson
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-dontwarn com.google.re2j.Matcher
-dontwarn com.google.re2j.Pattern

# OkHttp
-keep,allowobfuscation,allowshrinking class okhttp3.RequestBody
-keep,allowobfuscation,allowshrinking class okhttp3.ResponseBody

#Retrofit
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <3>

# Models
-keep class com.apkup.data.** { *; }
-keep class com.apkup.data.izzy.** { *; }
-keep class com.apkup.data.selfupdate.** { *; }
-keep class com.apkup.data.repomapping.** { *; }
-keep class com.apkup.repository.KnownRepoEntry { *; }

# Shizuku
-keep class rikka.shizuku.** { *; }
-keep interface rikka.shizuku.** { *; }
-keep class moe.shizuku.** { *; }
-keep interface moe.shizuku.** { *; }

# Strip verbose logging in release builds
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
}

# Google Play API (Aurora gplayapi) & Protobuf
-keep class com.aurora.gplayapi.** { *; }
-keep interface com.aurora.gplayapi.** { *; }
-dontwarn com.aurora.gplayapi.**

-keep class com.google.protobuf.** { *; }
-dontwarn com.google.protobuf.**

# Kotlinx Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class * {
    *** Companion;
}
-keepclassmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class * extends kotlinx.serialization.internal.GeneratedSerializer {
    <init>(...);
}
-keep class kotlinx.serialization.** { *; }

# SessionInstaller & InstallPart
-keep class com.apkup.util.SessionInstaller** { *; }
-keep class com.apkup.util.InstallPart { *; }

