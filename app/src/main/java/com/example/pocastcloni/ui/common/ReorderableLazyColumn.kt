package com.example.pocastcloni.ui.common

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableLazyColumn(
    items: ImmutableList<T>, // ImmutableList erzwingen (stabile Collection)
    key: (T) -> Any,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    reverseLayout: Boolean = false,
    itemContent: @Composable LazyItemScope.(index: Int, item: T, isDragging: Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()
    val state = rememberLazyListState()
    val view = LocalView.current

    // Stable state-holder (Instanz bleibt gleich, nur interne Snapshot-Werte ändern sich)
    val dragDropState = remember { DragDropState() }

    // pointerInput darf nicht mit "stale" Lambdas arbeiten, ohne neu zu starten
    val onReorderUpdated by rememberUpdatedState(onReorder)

    LazyColumn(
        state = state,
        modifier =
        modifier.pointerInput(reverseLayout) {
            // NICHT als Compose-State halten (UI interessiert sich nicht, vermeidet Snapshot-Reads im pointerInput)
            var scrollJob: Job? = null

            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val layoutInfo = state.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val viewportHeight = layoutInfo.viewportSize.height

                    val hitItem =
                        visibleItems.firstOrNull { item ->
                            val itemTop =
                                if (reverseLayout) {
                                    viewportHeight - item.offset - item.size - layoutInfo.beforeContentPadding
                                } else {
                                    item.offset
                                }
                            val itemBottom = itemTop + item.size
                            offset.y.toInt() in itemTop..itemBottom
                        }

                    hitItem?.let { itemInfo ->
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)

                        val initialVisualTop =
                            if (reverseLayout) {
                                viewportHeight - itemInfo.offset - itemInfo.size - layoutInfo.beforeContentPadding
                            } else {
                                itemInfo.offset
                            }.toFloat()

                        dragDropState.startDrag(
                            index = itemInfo.index,
                            initialVisualTop = initialVisualTop
                        )
                    }
                },
                onDrag = { change, _ ->
                    change.consume()
                    if (!dragDropState.hasActiveDrag()) return@detectDragGesturesAfterLongPress

                    // High-frequency update: NICHT im Composable-Body lesen, sondern nur Modifier/Draw.
                    val dragAmountY = change.position.y - change.previousPosition.y
                    dragDropState.dragBy(dragAmountY)

                    val layoutInfo = state.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val viewportHeight = layoutInfo.viewportSize.height

                    val draggedIndex = dragDropState.draggedIndexRaw()
                    val draggedItemInfo = visibleItems.find { it.index == draggedIndex } ?: return@detectDragGesturesAfterLongPress

                    val currentItemSize = draggedItemInfo.size
                    val currentItemCenter = dragDropState.draggedVisualTopRaw() + (currentItemSize / 2f)

                    // --- AUTO SCROLL LOGIK ---
                    val viewportStart = layoutInfo.viewportStartOffset
                    val viewportEnd = layoutInfo.viewportEndOffset
                    val viewportLen = viewportEnd - viewportStart

                    val topZone = viewportLen * 0.15f
                    val bottomZone = viewportLen * 0.85f

                    val minSpeed = 3f
                    val maxSpeed = 15f
                    var scrollAmount = 0f

                    if (currentItemCenter < topZone) {
                        val ratio = (topZone - currentItemCenter) / topZone
                        val speed = minSpeed + (maxSpeed - minSpeed) * ratio
                        val finalSpeed = min(speed, maxSpeed)
                        scrollAmount = if (reverseLayout) finalSpeed else -finalSpeed
                    } else if (currentItemCenter > bottomZone) {
                        val ratio = (currentItemCenter - bottomZone) / (viewportLen - bottomZone)
                        val speed = minSpeed + (maxSpeed - minSpeed) * ratio
                        val finalSpeed = min(speed, maxSpeed)
                        scrollAmount = if (reverseLayout) -finalSpeed else finalSpeed
                    }

                    if (scrollAmount != 0f) {
                        if (scrollJob?.isActive != true) {
                            scrollJob =
                                scope.launch {
                                    while (true) {
                                        val consumed = state.scrollBy(scrollAmount)
                                        if (consumed == 0f) break
                                        delay(10)
                                    }
                                }
                        }
                    } else {
                        scrollJob?.cancel()
                        scrollJob = null
                    }

                    // --- SWAP LOGIK ---
                    val targetItem =
                        visibleItems.find { itemInfo ->
                            if (itemInfo.index == draggedIndex) return@find false

                            val targetTop =
                                if (reverseLayout) {
                                    viewportHeight - itemInfo.offset - itemInfo.size - layoutInfo.beforeContentPadding
                                } else {
                                    itemInfo.offset
                                }
                            val targetBottom = targetTop + itemInfo.size

                            currentItemCenter.toInt() in targetTop..targetBottom
                        }

                    if (targetItem != null) {
                        val oldIndex = draggedIndex
                        val newIndex = targetItem.index

                        onReorderUpdated(oldIndex, newIndex)
                        dragDropState.updateDraggedIndex(newIndex)

                        view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    }
                },
                onDragEnd = {
                    dragDropState.endDrag()
                    scrollJob?.cancel()
                    scrollJob = null
                },
                onDragCancel = {
                    dragDropState.endDrag()
                    scrollJob?.cancel()
                    scrollJob = null
                }
            )
        },
        contentPadding = contentPadding,
        verticalArrangement = verticalArrangement,
        reverseLayout = reverseLayout
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { index, item ->
            // LOW frequency read (Start/Swap/End) -> okay in Composition
            val draggedIndex = dragDropState.draggedItemIndex
            val isDragging = index == draggedIndex

            val elevation by animateDpAsState(
                targetValue = if (isDragging) 6.dp else 0.dp,
                label = "elevation"
            )

            Box(
                modifier =
                Modifier
                    .fillParentMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        // High-frequency read NUR für das gezogene Item, und NUR in der Modifier-Phase.
                        translationY =
                            if (isDragging) {
                                val layoutInfo = state.layoutInfo
                                val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }
                                if (itemInfo != null) {
                                    val layoutTop =
                                        if (reverseLayout) {
                                            layoutInfo.viewportSize.height -
                                                itemInfo.offset -
                                                itemInfo.size -
                                                layoutInfo.beforeContentPadding
                                        } else {
                                            itemInfo.offset
                                        }
                                    dragDropState.draggedItemVisualTop - layoutTop
                                } else {
                                    0f
                                }
                            } else {
                                0f
                            }

                        scaleX = 1f
                        scaleY = 1f
                        shadowElevation = elevation.toPx()
                    }
                    .background(Color.Transparent)
            ) {
                itemContent(index, item, isDragging)
            }
        }
    }
}

@Stable
private class DragDropState {
    // UI-observed (Snapshot) - wird von der UI gelesen
    var draggedItemIndex by mutableIntStateOf(-1)
        private set
    var draggedItemVisualTop by mutableFloatStateOf(0f)
        private set
    var isDragging by mutableStateOf(false)
        private set

    // Raw values für Gesture-Logik (verhindert Snapshot-Reads im pointerInput)
    private var draggedItemIndexRaw: Int = -1
    private var draggedItemVisualTopRaw: Float = 0f
    private var isDraggingRaw: Boolean = false

    fun hasActiveDrag(): Boolean = isDraggingRaw

    fun draggedIndexRaw(): Int = draggedItemIndexRaw

    fun draggedVisualTopRaw(): Float = draggedItemVisualTopRaw

    fun startDrag(
        index: Int,
        initialVisualTop: Float
    ) {
        draggedItemIndexRaw = index
        draggedItemVisualTopRaw = initialVisualTop
        isDraggingRaw = true

        // Snapshot writes (UI updates)
        draggedItemIndex = index
        draggedItemVisualTop = initialVisualTop
        isDragging = true
    }

    fun dragBy(deltaY: Float) {
        draggedItemVisualTopRaw += deltaY
        // Snapshot write (nur das gezogene Item invalidiert seinen graphicsLayer)
        draggedItemVisualTop = draggedItemVisualTopRaw
    }

    fun updateDraggedIndex(newIndex: Int) {
        draggedItemIndexRaw = newIndex
        draggedItemIndex = newIndex
    }

    fun endDrag() {
        isDraggingRaw = false
        draggedItemIndexRaw = -1
        draggedItemVisualTopRaw = 0f

        isDragging = false
        draggedItemIndex = -1
        draggedItemVisualTop = 0f
    }
}
