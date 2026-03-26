# ProGuard rules for PocastCloni - Production Quality Configuration

# --- General Best Practices ---
# Keep essential metadata for reflection and debugging.
# MethodParameters is required by the Jackson Kotlin module to read constructor
# parameter names when deserializing Kotlin data classes (RssFeed, RssItem, etc.).
-keepattributes Signature,InnerClasses,*Annotation*,MethodParameters

# Keep all classes and all members (public AND private) in the app package.
# The "public *" variant is insufficient: R8 full mode removes private suspend functions
# and coroutine state-machine inner classes ($invoke$N) because they are not reachable
# through normal call-graph analysis. This broader rule prevents that.
-keep class com.example.pocastcloni.** { *; }


# --- Kotlin & Coroutines ---
# Retrofit 2.6+ detects suspend functions via Class.forName("kotlin.coroutines.Continuation").
# If this class is renamed by R8, Retrofit silently treats all suspend functions as non-suspend,
# causing IllegalArgumentException at call time.
-keep interface kotlin.coroutines.Continuation
# Keep coroutine metadata for stack traces.
-keepnames class kotlin.coroutines.jvm.internal.SuspendLambda


# --- Retrofit & OkHttp ---
# Official rules from https://github.com/square/retrofit (retrofit2.pro consumer rules).
# R8 full mode sees no subtypes of Retrofit interfaces (created via Proxy) and replaces them
# with null. The -if/-keep pair below prevents that optimization.
-dontwarn retrofit2.**
-dontwarn aQute.bnd.annotation.spi.ServiceProvider
-dontwarn javax.annotation.**
-dontwarn kotlin.Unit
-dontwarn retrofit2.KotlinExtensions
-dontwarn retrofit2.KotlinExtensions$*
# Keep annotation metadata so Retrofit can read @GET, @Query, etc. at runtime.
-keepattributes RuntimeVisibleAnnotations, RuntimeVisibleParameterAnnotations, AnnotationDefault
# Keep interface methods annotated with Retrofit HTTP annotations.
-keepclassmembers,allowshrinking,allowobfuscation interface * {
    @retrofit2.http.* <methods>;
}
# Prevent R8 from replacing Retrofit interface references with null (full mode).
-if interface * { @retrofit2.http.* <methods>; }
-keep,allowobfuscation interface <1>
# Keep the Jackson converter factory and its converters.
# R8 removes these because it cannot trace through Retrofit's internal reflective usage.
-keep class retrofit2.converter.jackson.** { *; }
# Keep retrofit2.Response and okhttp3.ResponseBody with their original names.
# PodcastService.fetchRawFeed has a Signature attribute that embeds these class names.
# R8 renames them (e.g. retrofit2.Response -> o6.W) but may not update the Signature
# correctly in all cases. Keeping them prevents the rename so the Signature stays resolvable
# at runtime when Retrofit calls method.getGenericParameterTypes().
-keep class retrofit2.Response { *; }
-keep class okhttp3.ResponseBody { *; }

# Specific keep rules for data classes used by Jackson (JSON via ObjectMapper).
-keep class com.example.pocastcloni.data.remote.ItunesResponse { *; }
-keep class com.example.pocastcloni.data.remote.ItunesPodcastDto { *; }
-keep class com.example.pocastcloni.data.local.BackupData { *; }
-keep class com.example.pocastcloni.data.local.BackupPodcast { *; }
-keep class com.example.pocastcloni.data.local.BackupFavorite { *; }
-keep class com.example.pocastcloni.domain.repository.UserSettings { *; }
-keep class com.example.pocastcloni.domain.repository.IndicatorSettings { *; }


# --- Jackson XML (jackson-dataformat-xml + jackson-module-kotlin) ---
# The app uses XmlMapper to parse RSS feeds into RssFeed / RssChannel / RssItem.
# Jackson relies on reflection and annotation processing at runtime.
# Without these rules R8 strips constructor parameter names and annotation data,
# causing all deserialized fields to be null (silent parse failure).
-dontwarn com.fasterxml.jackson.**
-keep class com.fasterxml.jackson.** { *; }
-keep @com.fasterxml.jackson.annotation.JsonIgnoreProperties class * { *; }
-keep @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlRootElement class * { *; }
-keepclassmembers class * {
    @com.fasterxml.jackson.annotation.JsonProperty *;
    @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlProperty *;
    @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlCData *;
    @com.fasterxml.jackson.dataformat.xml.annotation.JacksonXmlElementWrapper *;
}

# Keep RSS model classes in full so Jackson can reflect on them at runtime.
-keep class com.example.pocastcloni.data.remote.RssFeed { *; }
-keep class com.example.pocastcloni.data.remote.RssChannel { *; }
-keep class com.example.pocastcloni.data.remote.RssItem { *; }
-keep class com.example.pocastcloni.data.remote.RssImage { *; }
-keep class com.example.pocastcloni.data.remote.RssEnclosure { *; }


# --- Room Database ---
# These rules are necessary to prevent Room's generated code and type converters
# from being removed or renamed by ProGuard.
-keep class * extends androidx.room.TypeConverter
-keepclassmembers class * {
    @androidx.room.TypeConverter *;
}
