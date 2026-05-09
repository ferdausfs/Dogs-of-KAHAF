# Guardian Shield ProGuard / R8 rules

# Keep Hilt
-keep class dagger.hilt.** { *; }
-keep class * extends dagger.hilt.android.lifecycle.HiltViewModel { *; }
-keep @dagger.hilt.android.lifecycle.HiltViewModel class * { *; }

# Keep Room entities + DAOs
-keep class com.kahaf.guardianshield.data.db.entity.** { *; }
-keep class com.kahaf.guardianshield.data.db.dao.** { *; }

# Keep TFLite native
-keep class org.tensorflow.lite.** { *; }
-dontwarn org.tensorflow.lite.**

# Keep AccessibilityService + receivers (system-bound)
-keep class com.kahaf.guardianshield.service.** { *; }

# Keep kotlinx serialization
-keepclassmembers class kotlinx.serialization.json.** { *; }
-keep,includedescriptorclasses class com.kahaf.guardianshield.**$$serializer { *; }

# Compose
-keep class androidx.compose.runtime.** { *; }
-dontwarn androidx.compose.**
