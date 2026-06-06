package com.vayunmathur.games.solitaire.ui

import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.Dp
import com.vayunmathur.games.solitaire.data.Card
import com.vayunmathur.games.solitaire.util.SolitaireViewModel
import kotlin.math.roundToInt

@Composable
fun DraggableCard(
    card: Card,
    cards: List<Card>,
    sourceId: String,
    viewModel: SolitaireViewModel,
    cardWidth: Dp = CARD_WIDTH,
    cardHeight: Dp = CARD_HEIGHT,
    content: @Composable () -> Unit
) {
    var isDragging by remember { mutableStateOf(false) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var startPos by remember { mutableStateOf(Offset.Zero) }

    Box(
        modifier = Modifier
            .onGloballyPositioned { coords ->
                startPos = coords.positionInRoot()
            }
            .pointerInput(card, sourceId) {
                detectDragGestures(
                    onDragStart = {
                        isDragging = true
                        dragOffset = Offset.Zero
                        viewModel.startDrag(cards, sourceId)
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        dragOffset += dragAmount
                        viewModel.updateDrag(startPos + dragOffset)
                    },
                    onDragEnd = {
                        viewModel.endDrag(startPos + dragOffset)
                        isDragging = false
                        dragOffset = Offset.Zero
                    },
                    onDragCancel = {
                        viewModel.cancelDrag()
                        isDragging = false
                        dragOffset = Offset.Zero
                    }
                )
            }
            .then(
                if (isDragging) Modifier
                    .offset { IntOffset(dragOffset.x.roundToInt(), dragOffset.y.roundToInt()) }
                    .graphicsLayer { alpha = 0.8f }
                else Modifier
            )
    ) {
        content()
    }
}

@Composable
fun DropTarget(
    targetId: String,
    viewModel: SolitaireViewModel,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier.onGloballyPositioned { coords ->
            val pos = coords.positionInRoot()
            val size = coords.size
            viewModel.dropTargets[targetId] = androidx.compose.ui.geometry.Rect(
                pos.x, pos.y,
                pos.x + size.width, pos.y + size.height
            )
        }
    ) {
        content()
    }
}
