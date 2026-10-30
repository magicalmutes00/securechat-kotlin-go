# SecureChat ProGuard Rules

# ============================================
# Kotlin & Coroutines
# ============================================
-keep class kotlin.coroutines.** { *; }
-keep class kotlinx.coroutines.** { *; }
-keepclassmembers class ** {
    @kotlinx.coroutines.** *;
}

# ============================================
# Kotlinx Serialization
# ============================================
-keep class kotlinx.serialization.** { *; }
-keepclassmembers class ** {
    @kotlinx.serialization.** *;
}
-keep @kotlinx.serialization.Serializable class *
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }

# ============================================
# Hilt / Dagger
# ============================================
-keep class dagger.hilt.** { *; }
-keep class com.google.dagger.hilt.** { *; }
-keep class androidx.hilt.** { *; }
-keepclassmembers class ** {
    @dagger.hilt.** *;
    @com.google.dagger.hilt.** *;
}
# Hilt generated code
-keep class **_HiltComponents.* { *; }
-keep class **_GeneratedRootComponent.* { *; }

# ============================================
# Room
# ============================================
-keep class androidx.room.** { *; }
-keep class com.securechat.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }

# ============================================
# Ktor
# ============================================
-keep class io.ktor.** { *; }
-keepclassmembers class ** {
    @io.ktor.** *;
}
-keep class kotlinx.coroutines.** { *; }
-keep class kotlinx.serialization.** { *; }

# Ktor probes the JVM's management API to detect an attached debugger
# (io.ktor.util.debug.IntellijIdeaDebugDetector). java.lang.management does not
# exist on Android - there is no JVM there to manage - and the detector only
# serves Ktor's IntelliJ integration, so it is never reached on device. R8 fails
# a release build on the unresolved references, so they are suppressed here
# rather than by narrowing the broad io.ktor keep rule above, which exists to
# protect Ktor's reflective internals.
-dontwarn java.lang.management.ManagementFactory
-dontwarn java.lang.management.RuntimeMXBean

# ============================================
# Coil
# ============================================
-keep class coil.** { *; }
-keepclassmembers class ** {
    @coil.** *;
}

# ============================================
# WorkManager
# ============================================
-keep class androidx.work.** { *; }

# ============================================
# Security
# ============================================
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }

# ============================================
# OkHttp
# ============================================
-keep class okhttp3.** { *; }
-keep class okio.** { *; }
-keepclassmembers class ** {
    @okhttp3.** *;
}

# ============================================
# App Specific
# ============================================
-keep class com.securechat.SecureChatApp { *; }
-keep class com.securechat.MainActivity { *; }
-keep class com.securechat.core.security.** { *; }
-keep class com.securechat.data.repository.** { *; }
-keep class com.securechat.domain.** { *; }

# ============================================
# Remove Logging in Release
# ============================================
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** v(...);
    public static *** i(...);
    public static *** w(...);
}
-assumenosideeffects class kotlin.io.ConsoleKt {
    public static void print(...);
    public static void println(...);
}