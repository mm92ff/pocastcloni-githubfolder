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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.pocastcloni.R
import com.example.pocastcloni.ui.theme.Motion
import kotlinx.collections.immutable.ImmutableList
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableLazyColumn(
    items: ImmutableList<T>, // Enforce ImmutableList (stable collection)
    key: (T) -> Any,
    itemLabel: (T) -> String,
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

    // Stable state-holder (instance stays the same; only internal snapshot values change)
    val dragDropState = remember { DragDropState() }

    // pointerInput must not work with stale lambdas without restarting
    val onReorderUpdated by rememberUpdatedState(onReorder)

    LazyColumn(
        state = state,
        modifier =
        modifier.pointerInput(reverseLayout) {
            // Not held as Compose State (UI doesn't care; avoids snapshot reads inside pointerInput)
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

                    // High-frequency update: read only in Modifier/Draw phase, not in the Composable body.
                    val dragAmountY = change.position.y - change.previousPosition.y
                    dragDropState.dragBy(dragAmountY)

                    val layoutInfo = state.layoutInfo
                    val visibleItems = layoutInfo.visibleItemsInfo
                    val viewportHeight = layoutInfo.viewportSize.height

                    val draggedIndex = dragDropState.draggedIndexRaw()
                    val draggedItemInfo = visibleItems.find { it.index == draggedIndex } ?: return@detectDragGesturesAfterLongPress

                    val currentItemSize = draggedItemInfo.size
                    val currentItemCenter = dragDropState.draggedVisualTopRaw() + (currentItemSize / 2f)

                    // --- AUTO SCROLL LOGIC ---
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

                    // --- SWAP LOGIC ---
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
            // Low-frequency read (Start/Swap/End) -> safe to read in composition
            val draggedIndex = dragDropState.draggedItemIndex
            val isDragging = index == draggedIndex

            val elevation by animateDpAsState(
                targetValue = if (isDragging) 6.dp else 0.dp,
                animationSpec = Motion.stateSpec(),
                label = "elevation"
            )
            val upTarget = if (reverseLayout) index + 1 else index - 1
            val downTarget = if (reverseLayout) index - 1 else index + 1
            val title = itemLabel(item)
            val moveUpLabel = stringResource(R.string.move_item_up, title)
            val moveDownLabel = stringResource(R.string.move_item_down, title)
            val reorderActions =
                buildList {
                    if (upTarget in items.indices) {
                        add(
                            CustomAccessibilityAction(moveUpLabel) {
                                onReorderUpdated(index, upTarget)
                                true
                            }
                        )
                    }
                    if (downTarget in items.indices) {
                        add(
                            CustomAccessibilityAction(moveDownLabel) {
                                onReorderUpdated(index, downTarget)
                                true
                            }
                        )
                    }
                }

            Box(
                modifier =
                Modifier
                    .fillParentMaxWidth()
                    .semantics { customActions = reorderActions }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        // High-frequency read only for the dragged item, and only in the Modifier phase.
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
    // UI-observed (Snapshot) - read by the UI
    var draggedItemIndex by mutableIntStateOf(-1)
        private set
    var draggedItemVisualTop by mutableFloatStateOf(0f)
        private set
    var isDragging by mutableStateOf(false)
        private set

    // Raw values for gesture logic (prevents snapshot reads inside pointerInput)
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
        // Snapshot write (only the dragged item invalidates its graphicsLayer)
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
