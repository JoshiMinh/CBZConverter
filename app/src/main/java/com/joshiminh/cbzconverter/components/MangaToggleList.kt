package com.joshiminh.cbzconverter.components

import android.net.Uri
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.joshiminh.cbzconverter.backend.MihonMangaEntry

@Composable
fun MangaToggleList(
    manga: List<MihonMangaEntry>,
    selectedUris: List<Uri>,
    onToggleSingle: (Uri, Boolean) -> Unit,
    onToggleGroup: (List<Uri>, Boolean) -> Unit
) {
    if (manga.isEmpty()) {
        Text("No CBZ files found")
        return
    }

    val selectedUriSet = remember(selectedUris) { selectedUris.toSet() }

    LazyColumn(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 300.dp)
    ) {
        items(
            items = manga,
            key = { entry -> entry.files.firstOrNull()?.uri?.toString() ?: entry.name }
        ) { entry ->
            var expanded by rememberSaveable(entry.files.firstOrNull()?.uri?.toString() ?: entry.name) {
                mutableStateOf(false)
            }
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = CardDefaults.shape
            ) {
                Column(Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val allSelected = entry.files.all { selectedUriSet.contains(it.uri) }
                        Checkbox(
                            checked = allSelected,
                            onCheckedChange = { checked ->
                                onToggleGroup(entry.files.map { it.uri }, checked)
                            }
                        )
                        Text(
                            entry.name,
                            modifier = Modifier
                                .weight(1f)
                                .padding(end = 8.dp),
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            modifier = Modifier
                                .padding(start = 8.dp)
                                .clickable { expanded = !expanded }
                        )
                    }
                    AnimatedVisibility(
                        visible = expanded,
                        enter = expandVertically(),
                        exit = shrinkVertically()
                    ) {
                        Column(Modifier.padding(start = 12.dp)) {
                            entry.files.forEach { file ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Checkbox(
                                        checked = selectedUriSet.contains(file.uri),
                                        onCheckedChange = { checked ->
                                            onToggleSingle(file.uri, checked)
                                        }
                                    )
                                    Text(
                                        file.name,
                                        maxLines = 3,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}