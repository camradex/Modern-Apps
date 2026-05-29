package com.vayunmathur.games.alchemist.ui

import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vayunmathur.games.alchemist.R
import com.vayunmathur.games.alchemist.Route
import com.vayunmathur.games.alchemist.util.AlchemistViewModel
import com.vayunmathur.games.alchemist.util.PlacedItem
import com.vayunmathur.library.util.NavBackStack
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    backStack: NavBackStack<Route>,
    viewModel: AlchemistViewModel,
    onOpenGameCenter: () -> Unit
) {
    val availableItems by viewModel.availableItems.collectAsState()
    val activeItems by viewModel.placedElements.collectAsState()

    var screenWidth by remember { mutableFloatStateOf(0f) }
    var panelWidth by remember { mutableFloatStateOf(0f) }
    var playAreaOffsetInWindow by remember { mutableStateOf(Offset.Zero) }

    // Tracking for the current item being "pulled out" of the sidebar (UI-only).
    var draggingInventoryId by remember { mutableStateOf<Long?>(null) }
    var draggingInventoryOffset by remember { mutableStateOf(Offset.Zero) }

    var contextMenuElementId by remember { mutableStateOf<Long?>(null) }
    var contextMenuExpanded by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.onGloballyPositioned { screenWidth = it.size.width.toFloat() },
        topBar = {
            TopAppBar(title = { Text(stringResource(R.string.app_name)) }, actions = {
                IconButton(onClick = onOpenGameCenter) {
                    Icon(
                        painterResource(id = android.R.drawable.btn_star_big_on), "Achievements"
                    )
                }
                com.vayunmathur.library.ui.BackupButtons(
                    datastoreNames = listOf("datastore_default")
                )
            })
        }) { paddingValues ->
        Box(
            Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // 1. PLAY AREA (Full Screen)
            Box(
                Modifier
                    .fillMaxSize()
                    .onGloballyPositioned {
                        playAreaOffsetInWindow = it.positionInWindow()
                    }) {
                activeItems.forEach { item ->
                    key(item.key) {
                        val density = LocalDensity.current
                        DraggableElement(item = item, onDragEnd = { finalOffset ->
                            val limit = with(density) {
                                screenWidth - panelWidth - 72.dp.toPx()
                            }
                            // DELETION: Triggered if any part of the item touches the sidebar.
                            if (finalOffset.x > limit) {
                                viewModel.removeElement(item.key)
                            } else {
                                viewModel.updateElementPosition(item.key, finalOffset)
                                viewModel.tryCombine(item.key, finalOffset)
                            }
                        }, onLongClick = {
                            contextMenuElementId = item.id
                            contextMenuExpanded = true
                        })
                    }
                }
            }

            // 2. SIDE PANEL (Overlay)
            Surface(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(140.dp)
                    .align(Alignment.CenterEnd)
                    .onGloballyPositioned { panelWidth = it.size.width.toFloat() },
                tonalElevation = 8.dp,
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f)
            ) {
                LazyColumn(
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    items(availableItems, key = { it.id }) { item ->
                        var itemPosInWindow by remember { mutableStateOf(Offset.Zero) }

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .onGloballyPositioned {
                                    itemPosInWindow = it.positionInWindow()
                                }
                                .pointerInput(item.id) {
                                    detectDragGestures(onDragStart = { startOffset ->
                                        draggingInventoryId = item.id
                                        val fingerInWindow = itemPosInWindow + startOffset
                                        draggingInventoryOffset = Offset(
                                            x = fingerInWindow.x - playAreaOffsetInWindow.x - 100f,
                                            y = fingerInWindow.y - playAreaOffsetInWindow.y - 100f
                                        )
                                    }, onDrag = { change, dragAmount ->
                                        change.consume()
                                        draggingInventoryOffset += dragAmount
                                    }, onDragEnd = {
                                        // Final Check: Drop it if it's clear of the sidebar.
                                        if (draggingInventoryOffset.x < (screenWidth - panelWidth - 72f)) {
                                            viewModel.placeElement(item.id, draggingInventoryOffset)
                                        }
                                        draggingInventoryId = null
                                    }, onDragCancel = {
                                        draggingInventoryId = null
                                    })
                                }) {
                            Box(
                                Modifier
                                    .size(64.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .combinedClickable(onLongClick = {
                                        contextMenuElementId = item.id
                                        contextMenuExpanded = true
                                    }, onClick = {})) { DynamicAlchemyIcon(item.id) }
                            Text(item.name ?: "", fontSize = 10.sp)
                        }
                    }
                }
            }

            // 3. GLOBAL DRAG OVERLAY
            draggingInventoryId?.let { id ->
                Box(Modifier
                    .offset {
                        IntOffset(
                            draggingInventoryOffset.x.roundToInt(),
                            draggingInventoryOffset.y.roundToInt()
                        )
                    }
                    .size(72.dp)) { DynamicAlchemyIcon(id) }
            }
        }

        if (contextMenuExpanded) {
            DropdownMenu(
                expanded = contextMenuExpanded,
                onDismissRequest = { contextMenuExpanded = false }) {
                DropdownMenuItem(text = { Text(stringResource(R.string.see_details)) }, onClick = {
                    contextMenuExpanded = false
                    contextMenuElementId?.let {
                        backStack.add(Route.ItemDetails(it.toInt()))
                    }
                })
            }
        }
    }
}

@Composable
fun DraggableElement(
    item: PlacedItem,
    onDragEnd: (Offset) -> Unit,
    onLongClick: () -> Unit
) {
    var currentOffset by remember(item.key) { mutableStateOf(item.offset) }

    Box(
        Modifier
            .offset {
                IntOffset(currentOffset.x.roundToInt(), currentOffset.y.roundToInt())
            }
            .size(72.dp)
            .combinedClickable(onLongClick = onLongClick, onClick = {})
            .pointerInput(item.key) {
                detectDragGestures(onDrag = { change, dragAmount ->
                    change.consume()
                    currentOffset += dragAmount
                }, onDragEnd = { onDragEnd(currentOffset) })
            }) { DynamicAlchemyIcon(item.id) }
}
