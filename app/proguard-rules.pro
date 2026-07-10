# ===== WebDAV Server ProGuard / R8 rules =====

# Keep Kotlin metadata
-keepattributes *Annotation*, InnerClasses, Signature, EnclosingMethod
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations

# ---- Ktor (CIO) — keep engine/reflection hotspots, allow R8 to strip the rest ----
-keep class io.ktor.server.cio.** { *; }
-keep class io.ktor.server.engine.** { *; }
-keep class io.ktor.server.application.** { *; }
-keep class io.ktor.server.routing.** { *; }
-keep class io.ktor.server.request.** { *; }
-keep class io.ktor.server.response.** { *; }
-keep class io.ktor.http.** { *; }
-keep class io.ktor.util.** { *; }
-keep class io.ktor.utils.io.** { *; }
-keep class io.ktor.network.** { *; }
-dontwarn io.ktor.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.slf4j.**

# Service loaders used by engines
-keepnames class * implements java.util.ServiceLoader
-keepclassmembers class * {
    @kotlin.jvm.JvmField public static *** INSTANCE;
}

# ---- SLF4J (pulled by Ktor; no backend on Android) ----
-dontwarn org.slf4j.impl.**
-dontwarn org.slf4j.**

# ---- Kotlin coroutines ----
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# ---- DataStore ----
-dontwarn androidx.datastore.**

# ---- EncryptedSharedPreferences / Tink (security-crypto) ----
# R8 full mode fails without these: Tink references optional annotation jars
# (errorprone, javax.annotation) that are not on the Android runtime classpath.
-keep class com.google.crypto.tink.** { *; }
-keep class androidx.security.crypto.** { *; }
-dontwarn com.google.crypto.tink.**
-dontwarn com.google.errorprone.annotations.**
-dontwarn javax.annotation.**
-dontwarn javax.annotation.concurrent.**

# ---- ZXing (QR) ----
-keep class com.google.zxing.qrcode.** { *; }
-keep class com.google.zxing.BarcodeFormat { *; }
-keep class com.google.zxing.EncodeHintType { *; }
-dontwarn com.google.zxing.**

# ---- App models (JSON / enums by name) ----
-keep class com.localdev.webdavserver.data.local.model.** { *; }

-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# Suppress optional JVM deps pulled by Ktor / protobuf
-dontwarn java.lang.management.**
-dontwarn javax.naming.**
-dontwarn org.bouncycastle.**
-dontwarn com.google.protobuf.**
