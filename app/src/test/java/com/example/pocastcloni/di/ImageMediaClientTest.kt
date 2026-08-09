package com.example.pocastcloni.di

import okhttp3.Cache
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Response
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class ImageMediaClientTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `image client disables http cache without mutating approved client`() {
        val interceptor = NoOpInterceptor()
        val approvedClient =
            OkHttpClient.Builder()
                .cache(Cache(temporaryFolder.newFolder("approved-http-cache"), 1_024L * 1_024L))
                .followRedirects(false)
                .followSslRedirects(false)
                .addInterceptor(interceptor)
                .build()

        val imageClient = NetworkModule.provideImageMediaClient(approvedClient)

        try {
            assertNotNull(approvedClient.cache)
            assertNull(imageClient.cache)
            assertEquals(approvedClient.followRedirects, imageClient.followRedirects)
            assertEquals(approvedClient.followSslRedirects, imageClient.followSslRedirects)
            assertEquals(approvedClient.interceptors, imageClient.interceptors)
            assertSame(approvedClient.dispatcher, imageClient.dispatcher)
            assertSame(approvedClient.connectionPool, imageClient.connectionPool)
        } finally {
            approvedClient.cache?.close()
        }
    }

    private class NoOpInterceptor : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response = chain.proceed(chain.request())
    }
}
