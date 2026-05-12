# ===== Hilt =====
-keepclasseswithmembernames class * { @dagger.* <fields>; }
-keep class dagger.hilt.** { *; }
-keep @dagger.hilt.android.HiltAndroidApp class * { *; }
-keep @dagger.hilt.android.AndroidEntryPoint class * { *; }
-keep @dagger.hilt.InstallIn class * { *; }
-dontwarn dagger.hilt.**
-dontwarn javax.annotation.**

# ===== Room =====
-keep class * extends androidx.room.RoomDatabase
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keep @androidx.room.TypeConverters class * { *; }
-dontwarn androidx.room.**

# ===== TFLite =====
-keep class org.tensorflow.** { *; }
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-dontwarn org.tensorflow.**

# ===== Kotlin Coroutines =====
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**

# ===== DataStore =====
-keep class androidx.datastore.** { *; }
-dontwarn androidx.datastore.**

# ===== Security Crypto =====
-keep class androidx.security.crypto.** { *; }
-dontwarn androidx.security.crypto.**

# ===== Guardian Shield — সব class রাখো =====
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

# ===== Timber =====
-dontwarn org.jetbrains.annotations.**
-keep class timber.log.** { *; }

# ===== Android =====
-keep class * extends android.app.Service
-keep class * extends android.content.BroadcastReceiver
-keep class * extends android.accessibilityservice.AccessibilityService
-keep class * extends android.app.admin.DeviceAdminReceiver

# ===== ViewBinding =====
-keep class * implements androidx.viewbinding.ViewBinding {
    public static *** inflate(android.view.LayoutInflater);
    public static *** inflate(android.view.LayoutInflater, android.view.ViewGroup, boolean);
    public static *** bind(android.view.View);
}

# ===== Enum keep =====
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# ===== Serializable =====
-keepclassmembers class * implements java.io.Serializable {
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}