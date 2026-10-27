# ====================================================================
# App Production Release ProGuard / R8 Rules
# ====================================================================

# Preserve line numbers and source file attributes for crash reporting
-keepattributes SourceFile,LineNumberTable,Signature,*Annotation*,InnerClasses,EnclosingMethod

# Keep app data models & repositories from obfuscation/shrinking
-keep class com.example.fitness.** { *; }
-keepclassmembers class com.example.fitness.** { *; }

# Firebase Keep Rules
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keepclassmembers class * {
    @com.google.firebase.firestore.PropertyName <fields>;
}

# RevenueCat In-App Purchases
-keep class com.revenuecat.purchases.** { *; }
-dontwarn com.revenuecat.purchases.**

# OkHttp & Gson
-dontwarn okhttp3.**
-keep class okhttp3.** { *; }
-dontwarn com.google.gson.**
-keep class com.google.gson.** { *; }

# Coil Image Loader
-keep class coil.** { *; }
-dontwarn coil.**

# Credential Manager & Play Services
-keep class androidx.credentials.** { *; }
-dontwarn androidx.credentials.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ML Kit & CameraX
-keep class com.google.mlkit.** { *; }
-dontwarn com.google.mlkit.**
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

