# Guardian Shield ProGuard rules
-keep class org.tensorflow.lite.** { *; }
-keep class org.tensorflow.lite.gpu.** { *; }
-keep class com.guardian.shield.data.local.db.** { *; }
-keep class com.guardian.shield.domain.model.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase { *; }
-keep class * extends androidx.room.RoomDatabase
-keep class androidx.security.crypto.** { *; }
-keepattributes *Annotation*, InnerClasses, EnclosingMethod, Signature
-dontwarn org.tensorflow.lite.gpu.**
