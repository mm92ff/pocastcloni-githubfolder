package com.example.pocastcloni.ui.common

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
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
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun <T> ReorderableLazyVerticalGrid(
    items: ImmutableList<T>,
    key: (T) -> Any,
    itemLabel: (T) -> String,
    columns: GridCells,
    onReorder: (Int, Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.Top,
    horizontalArrangement: Arrangement.Horizontal = Arrangement.Start,
    reverseLayout: Boolean = false,
    onDragStartIndex: ((Int) -> Unit)? = null,
    itemContent: @Composable LazyGridItemScope.(index: Int, item: T, isDragging: Boolean) -> Unit
) {
    val state = rememberLazyGridState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    val dragDropState = remember { GridDragDropState() }

    val onReorderUpdated by rememberUpdatedState(onReorder)
    val onDragStartIndexUpdated by rememberUpdatedState(onDragStartIndex)

    var scrollJob by remember { mutableStateOf<Job?>(null) }

    LazyVerticalGrid(
        state = state,
        columns = columns,
        modifier =
        modifier.pointerInput(reverseLayout) {
            detectDragGesturesAfterLongPress(
                onDragStart = { offset ->
                    val layoutInfo = state.layoutInfo
                    val viewportHeight = layoutInfo.viewportSize.height

                    // Hit-Test: welche Kachel wurde "gegriffen"?
                    val hitItem =
                        layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            val left = item.offset.x
                            val right = left + item.size.width

                            val top =
                                if (reverseLayout) {
                                    viewportHeight -
                                        item.offset.y -
                                        item.size.height -
                                        layoutInfo.beforeContentPadding
                                } else {
                                    item.offset.y
                                }
                            val bottom = top + item.size.height

                            val x = offset.x.toInt()
                            val y = offset.y.toInt()
                            (x in left until right) && (y in top until bottom)
                        } ?: return@detectDragGesturesAfterLongPress

                    val initialX = hitItem.offset.x.toFloat()
                    val initialY =
                        run {
                            val top =
                                if (reverseLayout) {
                                    viewportHeight -
                                        hitItem.offset.y -
                                        hitItem.size.height -
                                        layoutInfo.beforeContentPadding
                                } else {
                                    hitItem.offset.y
                                }
                            top.toFloat()
                        }

                    dragDropState.startDrag(
                        index = hitItem.index,
                        initialVisualX = initialX,
                        initialVisualY = initialY
                    )

                    // Optional: Home kann hier Edit-Mode aktivieren (ohne extra Haptic)
                    onDragStartIndexUpdated?.invoke(hitItem.index)

                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                },
                onDrag = { change, dragAmount ->

                    if (!dragDropState.hasActiveDrag()) return@detectDragGesturesAfterLongPress

                    dragDropState.dragBy(dragAmount.x, dragAmount.y)

                    val layoutInfo = state.layoutInfo
                    val viewportHeight = layoutInfo.viewportSize.height

                    val draggedIndex = dragDropState.draggedIndexRaw()
                    val draggedInfo =
                        layoutInfo.visibleItemsInfo.firstOrNull { it.index == draggedIndex }
                            ?: return@detectDragGesturesAfterLongPress

                    val itemW = draggedInfo.size.width.toFloat()
                    val itemH = draggedInfo.size.height.toFloat()

                    val centerX = dragDropState.draggedVisualXRaw() + itemW / 2f
                    val centerY = dragDropState.draggedVisualYRaw() + itemH / 2f

                    // Auto-Scroll (nur vertikal)
                    run {
                        val viewportStart = layoutInfo.viewportStartOffset.toFloat()
                        val viewportEnd = layoutInfo.viewportEndOffset.toFloat()
                        val viewportLen = abs(viewportEnd - viewportStart).coerceAtLeast(1f)

                        val topZone = viewportStart + viewportLen * 0.15f
                        val bottomZone = viewportStart + viewportLen * 0.85f

                        val maxSpeed = 28f
                        val minSpeed = 6f

                        val scrollAmount: Float? =
                            when {
                                centerY < topZone -> {
                                    val ratio = ((topZone - centerY) / (viewportLen * 0.15f)).coerceIn(0f, 1f)
                                    val speed = minSpeed + (maxSpeed - minSpeed) * ratio
                                    if (reverseLayout) speed else -speed
                                }

                                centerY > bottomZone -> {
                                    val ratio = ((centerY - bottomZone) / (viewportLen * 0.15f)).coerceIn(0f, 1f)
                                    val speed = minSpeed + (maxSpeed - minSpeed) * ratio
                                    if (reverseLayout) -speed else speed
                                }

                                else -> null
                            }

                        if (scrollAmount != null && scrollJob == null) {
                            scrollJob =
                                scope.launch {
                                    while (dragDropState.hasActiveDrag()) {
                                        state.scrollBy(scrollAmount)
                                        delay(16)
                                    }
                                }
                        } else if (scrollAmount == null) {
                            scrollJob?.cancel()
                            scrollJob = null
                        }
                    }

                    // Swap-Target im Grid finden (X + Y)
                    val targetItem =
                        layoutInfo.visibleItemsInfo.firstOrNull { item ->
                            if (item.index == draggedIndex) return@firstOrNull false

                            val left = item.offset.x
                            val right = left + item.size.width

                            val top =
                                if (reverseLayout) {
                                    viewportHeight -
                                        item.offset.y -
                                        item.size.height -
                                        layoutInfo.beforeContentPadding
                                } else {
                                    item.offset.y
                                }
                            val bottom = top + item.size.height

                            val cx = centerX.toInt()
                            val cy = centerY.toInt()
                            (cx in left until right) && (cy in top until bottom)
                        }

                    if (targetItem != null) {
                        val newIndex = targetItem.index
                        val oldIndex = draggedIndex

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
        horizontalArrangement = horizontalArrangement,
        reverseLayout = reverseLayout
    ) {
        itemsIndexed(items = items, key = { _, item -> key(item) }) { index, item ->
            val draggedIndex = dragDropState.draggedItemIndex
            val isDragging = index == draggedIndex

            val elevation by animateDpAsState(
                targetValue = if (isDragging) 6.dp else 0.dp,
                animationSpec = Motion.stateSpec(),
                label = "gridElevation"
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
                    .semantics { customActions = reorderActions }
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        if (isDragging) {
                            val layoutInfo = state.layoutInfo
                            val viewportHeight = layoutInfo.viewportSize.height
                            val itemInfo = layoutInfo.visibleItemsInfo.firstOrNull { it.index == index }

                            if (itemInfo != null) {
                                val layoutX = itemInfo.offset.x.toFloat()
                                val layoutY =
                                    if (reverseLayout) {
                                        (
                                            viewportHeight -
                                                itemInfo.offset.y -
                                                itemInfo.size.height -
                                                layoutInfo.beforeContentPadding
                                            ).toFloat()
                                    } else {
                                        itemInfo.offset.y.toFloat()
                                    }

                                translationX = dragDropState.draggedItemVisualX - layoutX
                                translationY = dragDropState.draggedItemVisualY - layoutY
                            } else {
                                translationX = 0f
                                translationY = 0f
                            }
                        } else {
                            translationX = 0f
                            translationY = 0f
                        }

                        shadowElevation = elevation.toPx()
                    }
            ) {
                itemContent(index, item, isDragging)
            }
        }
    }
}

@Stable
private class GridDragDropState {
    var draggedItemIndex by mutableIntStateOf(-1)
        private set

    var draggedItemVisualX by mutableFloatStateOf(0f)
        private set

    var draggedItemVisualY by mutableFloatStateOf(0f)
        private set

    private var draggedItemIndexRaw: Int = -1
    private var draggedItemVisualXRaw: Float = 0f
    private var draggedItemVisualYRaw: Float = 0f
    private var isDraggingRaw: Boolean = false

    fun hasActiveDrag(): Boolean = isDraggingRaw

    fun draggedIndexRaw(): Int = draggedItemIndexRaw

    fun draggedVisualXRaw(): Float = draggedItemVisualXRaw

    fun draggedVisualYRaw(): Float = draggedItemVisualYRaw

    fun startDrag(
        index: Int,
        initialVisualX: Float,
        initialVisualY: Float
    ) {
        draggedItemIndexRaw = index
        draggedItemVisualXRaw = initialVisualX
        draggedItemVisualYRaw = initialVisualY
        isDraggingRaw = true

        draggedItemIndex = index
        draggedItemVisualX = initialVisualX
        draggedItemVisualY = initialVisualY
    }

    fun dragBy(
        deltaX: Float,
        deltaY: Float
    ) {
        draggedItemVisualXRaw += deltaX
        draggedItemVisualYRaw += deltaY

        draggedItemVisualX = draggedItemVisualXRaw
        draggedItemVisualY = draggedItemVisualYRaw
    }

    fun updateDraggedIndex(newIndex: Int) {
        draggedItemIndexRaw = newIndex
        draggedItemIndex = newIndex
    }

    fun endDrag() {
        draggedItemIndexRaw = -1
        draggedItemVisualXRaw = 0f
        draggedItemVisualYRaw = 0f
        isDraggingRaw = false

        draggedItemIndex = -1
        draggedItemVisualX = 0f
        draggedItemVisualY = 0f
    }
}
