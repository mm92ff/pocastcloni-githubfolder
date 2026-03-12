# ProGuard rules for PocastCloni - Production Quality Configuration

# --- General Best Practices ---
# Keep essential metadata for reflection and debugging.
-keepattributes Signature,InnerClasses,*Annotation*

# Keep all public classes and their public members in your application's main package.
# This is a strong safety net for reflection and serialization.
-keep public class com.example.pocastcloni.** { public *; }


# --- Kotlin & Coroutines ---
# This is the official, modern rule for keeping coroutine metadata.
-keepnames class kotlin.coroutines.jvm.internal.SuspendLambda


# --- Retrofit, OkHttp, and Gson ---
# Official recommended rules. These are crucial for networking to function correctly.
-dontwarn retrofit2.**
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-keep,allowobfuscation,allowshrinking interface retrofit2.http.**
-keep,allowobfuscation,allowshrinking @interface retrofit2.http.**

# Specific keep rules for data classes used by Gson.
# While the broad rule above is a safety net, explicit rules are clearer.
-keep class com.example.pocastcloni.data.remote.ItunesResponse { *; }
-keep class com.example.pocastcloni.data.remote.ItunesPodcastDto { *; }
-keep class com.example.pocastcloni.data.local.BackupData { *; }
-keep class com.example.pocastcloni.data.local.BackupPodcast { *; }


# --- TikXML ---
# Correctly keeps all classes annotated with @Xml and their necessary members for parsing.
-keep @com.tickaroo.tikxml.annotation.Xml class * { *; }
-keepclassmembers class ** {
    @com.tickaroo.tikxml.annotation.PropertyElement <fields>;
    @com.tickaroo.tikxml.annotation.Attribute <fields>;
    @com.tickaroo.tikxml.annotation.Element <fields>;
}


# --- Room Database ---
# These rules are necessary to prevent Room's generated code and type converters
# from being removed or renamed by ProGuard.
-keep class * extends androidx.room.TypeConverter
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
