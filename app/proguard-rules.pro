# Guardian Shield ProGuard / R8 rules

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep Room entities + DAOs
-keep class com.kahaf.guardianshield.data.db.entity.** { *; }
-keep class com.kahaf.guardianshield.data.db.dao.** { *; }

# Keep TFLite native (CRITICAL: NSFW classifier loads native delegates by reflection)
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class org.tensorflow.lite.nnapi.** { *; }
-keep class org.tensorflow.lite.support.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep AccessibilityService + receivers (system-bound)
-keep class com.kahaf.guardianshield.service.** { *; }
-keep class com.kahaf.guardianshield.admin.** { *; }

# v3.1.1: keep kotlinx serialization (the previous rules were incomplete —
# release builds would silently drop @Serializable classes' generated
# serializers and break Export/Import configuration).
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.kahaf.guardianshield.**$$serializer { *; }
-keepclassmembers class com.kahaf.guardianshield.** {
    *** Companion;
}
-keepclasseswithmembers class com.kahaf.guardianshield.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Compose
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**

# Coroutines
-dontwarn kotlinx.coroutines.**
-keepclassmembers class kotlinx.coroutines.** { *; }

# WorkManager + HiltWorker
-keep class * extends androidx.work.CoroutineWorker { *; }
-keep class * extends androidx.work.ListenableWorker { *; }
