package com.example.pocastcloni.domain.model

enum class FeedUpdateMode {
    ALWAYS_FULL,
    SMART_STREAM
    ;

    fun requiresForceFullRefresh(): Boolean = this == ALWAYS_FULL
}
