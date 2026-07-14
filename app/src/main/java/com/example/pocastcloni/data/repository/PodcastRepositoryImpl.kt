package com.example.pocastcloni.data.repository

import android.content.Context
import com.example.pocastcloni.data.local.PodcastDao
import com.example.pocastcloni.data.remote.ItunesSearchApi
import com.example.pocastcloni.di.DispatcherProvider
import com.example.pocastcloni.domain.repository.LibraryMaintenancePort
import com.example.pocastcloni.domain.repository.PodcastCommandPort
import com.example.pocastcloni.domain.repository.PodcastQueryPort
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PodcastRepositoryImpl
@Inject
constructor(
    podcastDao: PodcastDao,
    itunesSearchApi: ItunesSearchApi,
    dispatcherProvider: DispatcherProvider,
    @ApplicationContext context: Context
) : PodcastQueryPort by PodcastQueryAdapter(
    podcastDao = podcastDao,
    dispatcherProvider = dispatcherProvider,
    search = PodcastSearchAdapter(podcastDao, itunesSearchApi, dispatcherProvider)
),
    PodcastCommandPort by PodcastCommandAdapter(podcastDao, dispatcherProvider, context),
    LibraryMaintenancePort by PodcastMaintenanceAdapter(podcastDao, dispatcherProvider, context),
    PodcastPagingSource by PodcastPagingAdapter(podcastDao, dispatcherProvider)
