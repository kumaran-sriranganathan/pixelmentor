# MSAL
-keep class com.microsoft.identity.** { *; }
-keep class com.microsoft.aad.** { *; }

# Retrofit + OkHttp
-dontwarn okhttp3.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions

# Gson
-keep class com.pixelmentor.app.data.api.**Dto { *; }
-keepattributes *Annotation*
