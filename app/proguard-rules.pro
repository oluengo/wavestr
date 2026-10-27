# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class io.github.oluengo.wavestr.**$$serializer { *; }
-keepclassmembers class io.github.oluengo.wavestr.** {
    *** Companion;
}
-keepclasseswithmembers class io.github.oluengo.wavestr.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# OkHttp
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
