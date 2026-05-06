package com.vayunmathur.library.util

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.mimeTypes
import androidx.compose.ui.draganddrop.toAndroidDragEvent
import androidx.compose.ui.platform.LocalContext

/**
 * A helper to find the Activity from a Context.
 */
private fun Context.findActivity(): Activity? {
    var context = this
    while (context is ContextWrapper) {
        if (context is Activity) return context
        context = context.baseContext
    }
    return null
}

/**
 * A modifier that handles external drag and drop of files.
 */
fun Modifier.onFileDrop(
    onFilesDropped: (List<Uri>) -> Unit
): Modifier = this.composed {
    val context = LocalContext.current
    remember(context, onFilesDropped) {
        this.dragAndDropTarget(
            shouldStartDragAndDrop = { event ->
                event.mimeTypes().isNotEmpty()
            },
            target = object : DragAndDropTarget {
                override fun onDrop(event: DragAndDropEvent): Boolean {
                    val dragEvent = event.toAndroidDragEvent()
                    val activity = context.findActivity()
                    
                    // Request permissions for cross-app drag and drop URIs
                    activity?.requestDragAndDropPermissions(dragEvent)

                    val clipData = dragEvent.clipData ?: return false
                    val uris = mutableListOf<Uri>()
                    for (i in 0 until clipData.itemCount) {
                        clipData.getItemAt(i).uri?.let { uris.add(it) }
                    }
                    if (uris.isNotEmpty()) {
                        onFilesDropped(uris)
                        return true
                    }
                    return false
                }
            }
        )
    }
}
