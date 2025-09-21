package com.joshiminh.cbzconverter.components

import android.net.Uri
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentEnforcement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.consumeAllChanges
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.SelectedFileInfo

@Composable
fun SelectedFilesList(
    selectedFiles: List<Uri>,
    resolveInfo: (Uri) -> SelectedFileInfo,
    onMove: (fromIndex: Int, toIndex: Int) -> Unit,
    onRemove: (Uri) -> Unit
) {
    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 260.dp),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
    ) {
        itemsIndexed(selectedFiles, key = { _, uri -> uri }) { index, uri ->
            val info = resolveInfo(uri)
            Column {
                SelectedFileRow(
                    index = index,
                    uri = uri,
                    info = info,
                    canMoveUp = index > 0,
                    canMoveDown = index < selectedFiles.lastIndex,
                    onMoveUp = { onMove(index, index - 1) },
                    onMoveDown = { onMove(index, index + 1) },
                    onRemove = { onRemove(uri) }
                )
                if (index < selectedFiles.lastIndex) {
                    Divider(modifier = Modifier.padding(vertical = 4.dp))
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedFileRow(
    index: Int,
    uri: Uri,
    info: SelectedFileInfo,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit
) {
    val dragThreshold = with(LocalDensity.current) { 24.dp.toPx() }
    var accumulatedDrag by remember { mutableStateOf(0f) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .pointerInput(canMoveUp, canMoveDown) {
                detectDragGesturesAfterLongPress(
                    onDragEnd = { accumulatedDrag = 0f },
                    onDragCancel = { accumulatedDrag = 0f },
                    onDrag = { change, dragAmount ->
                        change.consumeAllChanges()
                        accumulatedDrag += dragAmount.y
                        when {
                            accumulatedDrag <= -dragThreshold && canMoveUp -> {
                                onMoveUp()
                                accumulatedDrag = 0f
                            }
                            accumulatedDrag >= dragThreshold && canMoveDown -> {
                                onMoveDown()
                                accumulatedDrag = 0f
                            }
                        }
                    }
                )
            },
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${index + 1}. ${info.displayName}",
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            info.parentName?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        CompositionLocalProvider(LocalMinimumInteractiveComponentEnforcement provides false) {
            IconButton(
                onClick = onMoveUp,
                enabled = canMoveUp,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowUpward,
                    contentDescription = "Move up",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onMoveDown,
                enabled = canMoveDown,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowDownward,
                    contentDescription = "Move down",
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(32.dp)
            ) {
                Icon(
                    imageVector = Icons.Filled.Delete,
                    contentDescription = "Remove from selection",
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}