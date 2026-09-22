# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.kts.

# Keep ML Kit face detection classes
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }

# Keep Room entities
-keep class com.smartguard.prototype.profile.** { *; }

# Keep Hilt generated components
-keep class dagger.hilt.** { *; }
-keep class javax.inject.** { *; }

# Keep CameraX
-keep class androidx.camera.** { *; }

# Keep Biometric
-keep class androidx.biometric.** { *; }
