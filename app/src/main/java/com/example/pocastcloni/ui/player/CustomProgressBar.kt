package com.example.pocastcloni.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import com.example.pocastcloni.ui.theme.Dimens

/**
 * Phase-aware ProgressBar:
 * - High-frequency Werte (Position/Buffer/Duration) werden erst in der Draw-Phase gelesen.
 * - PointerInput nutzt IntSize -> width/height sind Int: daher immer sauber nach Float konvertieren.
 */
@Composable
fun CustomProgressBar(
    currentPositionMs: () -> Long,
    bufferedPositionMs: () -> Long,
    durationMs: () -> Long,
    height: Dp,
    color: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    bufferedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimens.PROGRESS_BAR_BUFFERED_ALPHA),
    onSeekStart: (() -> Unit)? = null,
    onSeekEnd: (() -> Unit)? = null
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragProgress by remember { mutableFloatStateOf(0f) }

    Canvas(
        modifier =
        modifier
            .fillMaxWidth()
            .height(height)
            .pointerInput(Unit) {
                detectHorizontalDragGestures(
                    onDragStart = { offset ->
                        isDragging = true
                        onSeekStart?.invoke()

                        // pointerInput: size.width ist Int
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragProgress = (offset.x / width).coerceIn(0f, 1f)
                    },
                    onHorizontalDrag = { change, _ ->
                        val width = size.width.toFloat().coerceAtLeast(1f)
                        dragProgress = (change.position.x / width).coerceIn(0f, 1f)
                    },
                    onDragEnd = {
                        isDragging = false
                        val duration = durationMs().coerceAtLeast(0L)
                        val newPos = (dragProgress.toDouble() * duration.toDouble()).toLong()
                        onSeek(newPos)
                        onSeekEnd?.invoke()
                    },
                    onDragCancel = {
                        isDragging = false
                        onSeekEnd?.invoke()
                    }
                )
            }
            .pointerInput(Unit) {
                detectTapGestures { offset ->
                    val duration = durationMs().coerceAtLeast(0L)
                    if (duration <= 0L) return@detectTapGestures

                    val width = size.width.toFloat().coerceAtLeast(1f)
                    val tapProgress = (offset.x / width).coerceIn(0f, 1f)
                    val newPosition = (tapProgress.toDouble() * duration.toDouble()).toLong()
                    onSeek(newPosition)
                }
            }
    ) {
        // Canvas draw: size ist Size -> width/height sind Float
        val yCenter = size.height / 2f
        val strokeWidth = size.height.coerceAtLeast(1f)

        val duration = durationMs().coerceAtLeast(1L).toFloat()
        val currentProgress = (currentPositionMs().toFloat() / duration).coerceIn(0f, 1f)
        val bufferedProgress = (bufferedPositionMs().toFloat() / duration).coerceIn(0f, 1f)
        val visualProgress = if (isDragging) dragProgress else currentProgress

        // Track
        if (trackColor.alpha > 0f) {
            drawLine(
                color = trackColor,
                start = Offset(0f, yCenter),
                end = Offset(size.width, yCenter),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Buffered
        if (bufferedColor.alpha > 0f && bufferedProgress > 0f) {
            drawLine(
                color = bufferedColor,
                start = Offset(0f, yCenter),
                end = Offset(size.width * bufferedProgress, yCenter),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round
            )
        }

        // Current / Drag
        drawLine(
            color = color,
            start = Offset(0f, yCenter),
            end = Offset(size.width * visualProgress, yCenter),
            strokeWidth = strokeWidth,
            cap = StrokeCap.Round
        )
    }
}

/**
 * Backwards-compatible Overload (falls du noch Call-Sites hast, die Longs übergeben).
 */
@Composable
fun CustomProgressBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    height: Dp,
    color: Color,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    bufferedColor: Color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = Dimens.PROGRESS_BAR_BUFFERED_ALPHA),
    onSeekStart: (() -> Unit)? = null,
    onSeekEnd: (() -> Unit)? = null
) {
    val current by rememberUpdatedState(currentPositionMs)
    val buffered by rememberUpdatedState(bufferedPositionMs)
    val duration by rememberUpdatedState(durationMs)

    CustomProgressBar(
        currentPositionMs = { current },
        bufferedPositionMs = { buffered },
        durationMs = { duration },
        height = height,
        color = color,
        onSeek = onSeek,
        modifier = modifier,
        trackColor = trackColor,
        bufferedColor = bufferedColor,
        onSeekStart = onSeekStart,
        onSeekEnd = onSeekEnd
    )
}
