package com.example.pocastcloni.playback.infrastructure

import android.os.Handler
import androidx.media3.session.MediaController
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.android.asCoroutineDispatcher
import javax.inject.Inject

fun interface MediaDispatcherFactory {
    fun create(controller: MediaController): CoroutineDispatcher
}

class HandlerMediaDispatcherFactory
@Inject
constructor() : MediaDispatcherFactory {
    override fun create(controller: MediaController): CoroutineDispatcher =
        Handler(controller.applicationLooper).asCoroutineDispatcher()
}
