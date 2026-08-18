# ProGuard / R8 Rules for Argus Secure Messenger

# 1. KotlinX Serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.SerializationKt
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,allowobfuscation,allowshrinking class * {
    <fields>;
}
-keepclassmembers class * {
    @kotlinx.serialization.SerialName <fields>;
    @kotlinx.serialization.Serializable <fields>;
}

# 2. Argus Data Models & Remote Payloads
-keep class com.example.argus.data.model.** { *; }
-keep class com.example.argus.data.remote.** { *; }
-keep class com.example.argus.crypto.** { *; }

# 3. OkHttp & Okio
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }

# 4. BouncyCastle & Cryptography
-dontwarn org.bouncycastle.**
-keep class org.bouncycastle.** { *; }
-keep class androidx.security.crypto.** { *; }
-keep class androidx.biometric.** { *; }

# 5. Jetpack Compose
-keep class androidx.compose.** { *; }
