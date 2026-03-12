package com.example.pocastcloni.domain.player

import kotlinx.coroutines.flow.Flow

interface PlayerVisibilityProvider {
    val isPlayerVisible: Flow<Boolean>
}
