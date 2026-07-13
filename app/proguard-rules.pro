# Keep reflection metadata used by Jackson's Kotlin module. Retrofit, Room, Hilt and
# WorkManager provide their own consumer rules and must not be duplicated here.
-keepattributes Signature,InnerClasses,EnclosingMethod,*Annotation*,MethodParameters

# AndroidJUnitRunner starts inside the tested app process and calls these two facades
# before test discovery. Keep only its bootstrap bridge so release instrumentation can boot.
-keep,allowoptimization,allowobfuscation class androidx.tracing.Trace { *; }
-keep,allowoptimization,allowobfuscation class kotlin.LazyKt { *; }

# JSON models reached reflectively through Jackson.
-keep class com.example.pocastcloni.data.remote.ItunesResponse { *; }
-keep class com.example.pocastcloni.data.remote.ItunesPodcastDto { *; }
-keep class com.example.pocastcloni.data.local.BackupData { *; }
-keep class com.example.pocastcloni.data.local.BackupPodcast { *; }
-keep class com.example.pocastcloni.data.local.BackupEpisodeState { *; }
-keep class com.example.pocastcloni.data.local.BackupFavorite { *; }
-keep class com.example.pocastcloni.domain.repository.UserSettings { *; }
-keep class com.example.pocastcloni.domain.repository.IndicatorSettings { *; }
-keep class com.example.pocastcloni.data.worker.DownloadResumeMetadata { *; }

# Retrofit reflects a suspend function's response type from Continuation<T>.
# R8 full mode may otherwise strip that generic signature even though the
# annotated service method itself is retained by Retrofit's consumer rules.
-keep,allowoptimization,allowshrinking,allowobfuscation class kotlin.coroutines.Continuation
-keep,allowoptimization,allowshrinking,allowobfuscation class retrofit2.Response

# Enum names are part of the portable settings JSON contract.
-keep enum com.example.pocastcloni.domain.model.AppTheme { *; }
-keep enum com.example.pocastcloni.domain.model.AppColor { *; }
-keep enum com.example.pocastcloni.domain.model.BufferMode { *; }
-keep enum com.example.pocastcloni.domain.model.FeedUpdateMode { *; }
-keep enum com.example.pocastcloni.domain.model.GradientDirection { *; }
-keep enum com.example.pocastcloni.domain.model.LayoutMode { *; }
