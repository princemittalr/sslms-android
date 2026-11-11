# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.sslms.console.**$$serializer { *; }
-keepclassmembers class com.sslms.console.** { *** Companion; kotlinx.serialization.KSerializer serializer(...); }
# osmdroid
-dontwarn org.osmdroid.**
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
