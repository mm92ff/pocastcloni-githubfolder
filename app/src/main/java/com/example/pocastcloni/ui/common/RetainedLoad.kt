package com.example.pocastcloni.ui.common

import androidx.compose.runtime.Immutable
import com.example.pocastcloni.ui.UiText
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map

@Immutable
data class RetainedLoad<out T>(
    val loading: Boolean = true,
    val lastValue: T? = null,
    val error: UiText? = null
)

fun <T> Flow<T>.asRetainedLoad(errorMessage: UiText): Flow<RetainedLoad<T>> =
    flow {
        var lastValue: T? = null
        emitAll(
            this@asRetainedLoad.map { value ->
                lastValue = value
                RetainedLoad(loading = false, lastValue = value)
            }.catch { error ->
                if (error is CancellationException) throw error
                emit(
                    RetainedLoad(
                        loading = false,
                        lastValue = lastValue,
                        error = errorMessage
                    )
                )
            }
        )
    }

fun <T> Flow<RetainedLoad<T>>.retainLatestValue(): Flow<RetainedLoad<T>> =
    flow {
        var retainedValue: T? = null
        this@retainLatestValue.collect { load ->
            retainedValue = load.lastValue ?: retainedValue
            emit(load.copy(lastValue = retainedValue))
        }
    }
