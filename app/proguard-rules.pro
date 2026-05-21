# Keep Room entities
-keep class com.guardianshield.app.data.model.** { *; }
-keep class com.guardianshield.app.data.db.** { *; }
# Keep service / receiver entry points
-keep class com.guardianshield.app.service.** { *; }
-keep class com.guardianshield.app.receiver.** { *; }
# Material
-keep class com.google.android.material.** { *; }
# Coroutines
-keepclassmembernames class kotlinx.** { volatile <fields>; }
