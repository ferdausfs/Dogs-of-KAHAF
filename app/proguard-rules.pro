# ============================================================
#  Guardian Shield — ProGuard / R8 rules
# ============================================================

# ── Hilt / Dagger ─────────────────────────────────────────────
-keepclasseswithmembernames class * { @dagger.* <fields>; }
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.annotation.**

# ── Room ──────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-dontwarn androidx.room.**

# ── TensorFlow Lite ───────────────────────────────────────────
-keep class org.tensorflow.** { *; }
-dontwarn org.tensorflow.**

# ── Kotlin coroutines ────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ── AndroidX modules ──────────────────────────────────────────
-keep class androidx.datastore.** { *; }
-keep class androidx.security.crypto.** { *; }
-keep class androidx.work.** { *; }
-dontwarn androidx.work.**

# ── Guardian Shield app classes ───────────────────────────────
-keep class com.guardian.shield.domain.** { *; }
-keep class com.guardian.shield.data.local.db.** { *; }
-keep class com.guardian.shield.data.local.datastore.** { *; }
-keep class com.guardian.shield.service.** { *; }
-keep class com.guardian.shield.admin.** { *; }
-keep class com.guardian.shield.receiver.** { *; }
-keep class com.guardian.shield.ui.** { *; }
-keep class com.guardian.shield.viewmodel.** { *; }
-keep class com.guardian.shield.util.** { *; }
-keep class com.guardian.shield.GuardianApp { *; }
-keep class com.guardian.shield.BuildConfig { *; }

# ── Android framework components ──────────────────────────────
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.accessibilityservice.AccessibilityService
-keep class * extends android.app.admin.DeviceAdminReceiver
-keep class * extends androidx.work.Worker

# ── ViewBinding ──────────────────────────────────────────────
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# ── Enums ────────────────────────────────────────────────────
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ── Misc ─────────────────────────────────────────────────────
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.** { *; }
