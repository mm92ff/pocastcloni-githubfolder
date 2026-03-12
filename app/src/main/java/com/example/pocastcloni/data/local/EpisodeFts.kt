package com.example.pocastcloni.data.local

import androidx.room.Entity
import androidx.room.Fts4

@Entity(tableName = "episodes_fts")
@Fts4(contentEntity = EpisodeEntity::class)
data class EpisodeFts(
    val title: String,
    val description: String
)