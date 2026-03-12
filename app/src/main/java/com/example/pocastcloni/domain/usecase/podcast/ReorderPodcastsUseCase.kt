package com.example.pocastcloni.domain.usecase.podcast

import com.example.pocastcloni.domain.model.Podcast
import com.example.pocastcloni.domain.repository.PodcastRepository
import java.util.Collections
import javax.inject.Inject

class ReorderPodcastsUseCase @Inject constructor(
    private val repository: PodcastRepository
) {
    /**
     * Tauscht zwei Podcasts in der Liste und aktualisiert die Sortierreihenfolge in der Datenbank.
     * VERALTET: Nutze invoke(podcasts) für Drag & Drop.
     */
    suspend operator fun invoke(currentList: List<Podcast>, sourceUrl: String, targetUrl: String) {
        val mutableList = currentList.toMutableList()
        val sourceIndex = mutableList.indexOfFirst { it.rssUrl == sourceUrl }
        val targetIndex = mutableList.indexOfFirst { it.rssUrl == targetUrl }

        if (sourceIndex != -1 && targetIndex != -1) {
            Collections.swap(mutableList, sourceIndex, targetIndex)
            val updatedList = mutableList.mapIndexed { index, podcast ->
                podcast.copy(sortOrder = index.toLong())
            }
            repository.reorderPodcasts(updatedList)
        }
    }

    /**
     * Persistiert eine bereits neu sortierte Liste (z.B. nach Drag & Drop).
     * Setzt sortOrder basierend auf dem Index.
     */
    suspend operator fun invoke(podcasts: List<Podcast>) {
        val updatedList = podcasts.mapIndexed { index, podcast ->
            podcast.copy(sortOrder = index.toLong())
        }
        repository.reorderPodcasts(updatedList)
    }
}