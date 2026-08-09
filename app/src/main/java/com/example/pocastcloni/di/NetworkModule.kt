package com.example.pocastcloni.di

import android.content.Context
import com.example.pocastcloni.BuildConfig
import com.example.pocastcloni.data.remote.ErrorResponseBodyLimitInterceptor
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.data.remote.PodcastService
import com.example.pocastcloni.data.remote.SafeRedirectInterceptor
import com.example.pocastcloni.data.serialization.JsonMapperFactory
import com.example.pocastcloni.data.remote.PublicNetworkDns
import com.example.pocastcloni.data.remote.ApprovedLocalRequestInterceptor
import com.example.pocastcloni.data.remote.ApprovedOriginDns
import com.example.pocastcloni.data.remote.LocalNetworkAccessRegistry
import com.example.pocastcloni.util.hasSameOrigin
import com.example.pocastcloni.util.ConnectivityProvider
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.parseNetworkUrl
import com.example.pocastcloni.util.NetworkConnectivityProvider
import com.fasterxml.jackson.databind.ObjectMapper
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.Cache
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.jackson.JacksonConverterFactory
import timber.log.Timber
import java.util.concurrent.TimeUnit
import javax.inject.Named
import okhttp3.Dns
import javax.inject.Singleton

/**
 * Defines three network trust profiles: public clients reject local/private DNS answers, the
 * explicit local-feed client stays on one approved origin, and the approved-media client combines
 * exact-origin request checks with host-scoped DNS selection. Redirect handling remains manual in
 * every profile so each hop is evaluated against the same policy.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun bindConnectivityProvider(impl: NetworkConnectivityProvider): ConnectivityProvider

    companion object {
        @Provides
        @Singleton
        fun provideObjectMapper(): ObjectMapper = JsonMapperFactory.create()

        @Provides
        @Singleton
        fun provideLoggingInterceptor(): HttpLoggingInterceptor {
            return HttpLoggingInterceptor { message -> Timber.tag("OkHttp").d(message) }.apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }
        }

        @Provides
        @Singleton
        fun provideOkHttpClient(
            @ApplicationContext context: Context,
            loggingInterceptor: HttpLoggingInterceptor
        ): OkHttpClient {
            val cacheDir = context.cacheDir.resolve(Constants.Cache.HTTP_CACHE_DIR)
            val cache = Cache(cacheDir, 50 * 1024 * 1024L)

            return OkHttpClient.Builder()
                .cache(cache)
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(PublicNetworkDns())
                .addInterceptor(SafeRedirectInterceptor())
                .apply {
                    if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor)
                }
                .connectTimeout(Constants.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.Network.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.Network.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        @Named("LocalNetworkClient")
        fun provideLocalNetworkClient(
            @ApplicationContext context: Context,
            loggingInterceptor: HttpLoggingInterceptor
        ): OkHttpClient {
            val cache = Cache(context.cacheDir.resolve(Constants.Cache.LOCAL_HTTP_CACHE_DIR), 20 * 1024 * 1024L)
            return OkHttpClient.Builder()
                .cache(cache)
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(Dns.SYSTEM)
                .addInterceptor(
                    SafeRedirectInterceptor(
                        isAllowedUrl = { parseNetworkUrl(it, allowLocalNetwork = true) != null },
                        allowOriginChange = false
                    )
                )
                .apply { if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor) }
                .connectTimeout(Constants.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.Network.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.Network.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        @Provides
        @Singleton
        @Named("RssNetworkClient")
        fun provideRssNetworkClient(okHttpClient: OkHttpClient): OkHttpClient =
            okHttpClient.newBuilder()
                .addInterceptor(ErrorResponseBodyLimitInterceptor())
                .build()

        @Provides
        @Singleton
        @Named("LocalRssNetworkClient")
        fun provideLocalRssNetworkClient(
            @Named("LocalNetworkClient") localNetworkClient: OkHttpClient
        ): OkHttpClient =
            localNetworkClient.newBuilder()
                .addInterceptor(ErrorResponseBodyLimitInterceptor())
                .build()

        @Provides
        @Singleton
        @Named("ApprovedMediaClient")
        fun provideApprovedMediaClient(
            @ApplicationContext context: Context,
            loggingInterceptor: HttpLoggingInterceptor,
            registry: LocalNetworkAccessRegistry
        ): OkHttpClient {
            val cache = Cache(
                context.cacheDir.resolve(Constants.Cache.APPROVED_MEDIA_HTTP_CACHE_DIR),
                20 * 1024 * 1024L
            )
            return OkHttpClient.Builder()
                .cache(cache)
                .followRedirects(false)
                .followSslRedirects(false)
                .dns(ApprovedOriginDns(registry))
                .addInterceptor(ApprovedLocalRequestInterceptor(registry))
                .addInterceptor(
                    SafeRedirectInterceptor(
                        isAllowedUrl = { url ->
                            parseNetworkUrl(url) != null || registry.isApproved(url)
                        },
                        isAllowedRedirect = { source, target ->
                            val touchesApprovedOrigin =
                                registry.isApproved(source.toString()) ||
                                    registry.isApproved(target.toString())
                            !touchesApprovedOrigin || hasSameOrigin(source, target)
                        }
                    )
                )
                .apply { if (BuildConfig.DEBUG) addInterceptor(loggingInterceptor) }
                .connectTimeout(Constants.Network.CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .readTimeout(Constants.Network.READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .writeTimeout(Constants.Network.WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                .build()
        }

        /**
         * Keeps the approved-origin security policy while making Coil the sole image disk-cache
         * owner. The shared approved client retains its HTTP cache for playback consumers.
         */
        @Provides
        @Singleton
        @Named("ImageMediaClient")
        fun provideImageMediaClient(
            @Named("ApprovedMediaClient") approvedMediaClient: OkHttpClient
        ): OkHttpClient =
            approvedMediaClient.newBuilder()
                .cache(null)
                .build()

        @Provides
        @Singleton
        @Named("RssRetrofit")
        fun provideRssRetrofit(
            @Named("RssNetworkClient") okHttpClient: OkHttpClient
        ): Retrofit {
            return Retrofit.Builder()
                .baseUrl(Constants.Network.RSS_BASE_URL)
                .client(okHttpClient)
                .build()
        }

        @Provides
        @Singleton
        @Named("ItunesRetrofit")
        fun provideItunesRetrofit(
            okHttpClient: OkHttpClient,
            objectMapper: ObjectMapper
        ): Retrofit {
            return Retrofit.Builder()
                .baseUrl(Constants.Network.ITUNES_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(objectMapper))
                .build()
        }

        @Provides
        @Singleton
        fun providePodcastService(
            @Named("RssRetrofit") retrofit: Retrofit
        ): PodcastService {
            return retrofit.create(PodcastService::class.java)
        }

        @Provides
        @Singleton
        @Named("LocalPodcastService")
        fun provideLocalPodcastService(
            @Named("LocalRssNetworkClient") client: OkHttpClient
        ): PodcastService = Retrofit.Builder()
            .baseUrl(Constants.Network.RSS_BASE_URL)
            .client(client)
            .build()
            .create(PodcastService::class.java)

        @Provides
        @Singleton
        fun provideItunesSearchApi(
            @Named("ItunesRetrofit") retrofit: Retrofit
        ): ItunesSearchApi {
            return retrofit.create(ItunesSearchApi::class.java)
        }
    }
}
