package com.example.pocastcloni.data.remote

import com.example.pocastcloni.util.Constants
import com.fasterxml.jackson.annotation.JsonProperty
import retrofit2.http.GET
import retrofit2.http.Query

data class ItunesResponse(
    @JsonProperty(Constants.Itunes.KEY_RESULT_COUNT) val resultCount: Int,
    @JsonProperty(Constants.Itunes.KEY_RESULTS) val results: List<ItunesPodcastDto>
)

data class ItunesPodcastDto(
    @JsonProperty(Constants.Itunes.KEY_COLLECTION_NAME) val collectionName: String?,
    @JsonProperty(Constants.Itunes.KEY_ARTIST_NAME) val artistName: String?,
    @JsonProperty(Constants.Itunes.KEY_FEED_URL) val feedUrl: String?,
    @JsonProperty(Constants.Itunes.KEY_ARTWORK_URL_600) val artworkUrl600: String?
)

interface ItunesSearchApi {
    @GET(Constants.Itunes.SEARCH_URL)
    suspend fun searchPodcasts(
        @Query(Constants.Itunes.TERM) term: String
    ): ItunesResponse
}
