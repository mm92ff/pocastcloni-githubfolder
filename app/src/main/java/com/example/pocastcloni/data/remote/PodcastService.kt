package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.Constants
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Streaming
import retrofit2.http.Url

interface PodcastService {
    @Streaming
    @GET
    suspend fun fetchRawFeed(
        @Url url: String,
        @Header(Constants.Network.HEADER_IF_MODIFIED_SINCE) lastModified: String? = null,
        @Header(Constants.Network.HEADER_IF_NONE_MATCH) eTag: String? = null
    ): Response<ResponseBody>
}
