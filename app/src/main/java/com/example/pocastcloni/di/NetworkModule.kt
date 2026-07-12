package com.example.pocastcloni.di

import android.content.Context
import com.example.pocastcloni.BuildConfig
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.data.remote.PodcastService
import com.example.pocastcloni.data.remote.SafeRedirectInterceptor
import com.example.pocastcloni.data.remote.PublicNetworkDns
import com.example.pocastcloni.util.ConnectivityProvider
import com.example.pocastcloni.util.Constants
import com.example.pocastcloni.util.NetworkConnectivityProvider
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.xml.XmlMapper
import com.fasterxml.jackson.module.kotlin.registerKotlinModule
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
import java.io.File
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class NetworkModule {
    @Binds
    abstract fun bindConnectivityProvider(impl: NetworkConnectivityProvider): ConnectivityProvider

    companion object {
        @Provides
        @Singleton
        fun provideObjectMapper(): ObjectMapper {
            return ObjectMapper().apply {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }

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
            val cacheDir = File(context.cacheDir, "http_cache")
            val cache = Cache(cacheDir, 50 * 1024 * 1024L) // 50 MB Cache

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
        fun provideXmlMapper(): XmlMapper {
            return XmlMapper().apply {
                registerKotlinModule()
                configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            }
        }

        @Provides
        @Singleton
        @Named("RssRetrofit")
        fun provideRssRetrofit(
            okHttpClient: OkHttpClient,
            xmlMapper: XmlMapper
        ): Retrofit {
            return Retrofit.Builder()
                .baseUrl(Constants.Network.RSS_BASE_URL)
                .client(okHttpClient)
                .addConverterFactory(JacksonConverterFactory.create(xmlMapper))
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
        fun provideItunesSearchApi(
            @Named("ItunesRetrofit") retrofit: Retrofit
        ): ItunesSearchApi {
            return retrofit.create(ItunesSearchApi::class.java)
        }
    }
}
