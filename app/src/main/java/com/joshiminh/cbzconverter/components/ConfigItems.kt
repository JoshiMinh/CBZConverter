package com.joshiminh.cbzconverter.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun Spacer12Divider() {
    Spacer(Modifier.height(12.dp))
    Divider()
    Spacer(Modifier.height(12.dp))
}

@Composable
fun TitleWithInfo(
    title: String,
    infoText: String
) {
    var showInfo by remember { mutableStateOf(false) }

    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(title, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
        IconButton(onClick = { showInfo = true }) {
            Icon(Icons.Outlined.Info, contentDescription = "Info")
        }
    }

    if (showInfo) {
        AlertDialog(
            onDismissRequest = { showInfo = false },
            confirmButton = {
                TextButton(onClick = { showInfo = false }) { Text("OK") }
            },
            title = { Text(title) },
            text = { Text(infoText) }
        )
    }
}

@Composable
fun ConfigNumberItem(
    title: String,
    infoText: String,
    value: String,
    enabled: Boolean,
    onValidNumber: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    var error by remember { mutableStateOf<String?>(null) }

    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))

    OutlinedTextField(
        value = text,
        onValueChange = { input ->
            text = input
            val trimmed = input.trim()
            val intVal = trimmed.toIntOrNull()
            error = when {
                trimmed.isEmpty() -> "Enter a number"
                intVal == null -> "Enter a valid number"
                intVal <= 0 -> "Must be greater than 0"
                else -> null
            }
            if (error == null) {
                onValidNumber(trimmed)
            }
        },
        singleLine = true,
        enabled = enabled,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        isError = error != null,
        supportingText = { error?.let { Text(it) } }
    )
    Spacer(Modifier.height(4.dp))
}

/**
 * Text input that auto-applies to the VM on every change.
 * Blank => means "no override" and VM should fall back to default filename.
 */
@Composable
fun ConfigTextItem(
    title: String,
    infoText: String,
    text: String,
    enabled: Boolean,
    supportingText: String,
    onTextChange: (String) -> Unit
) {
    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))
    OutlinedTextField(
        value = text,
        onValueChange = onTextChange,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.fillMaxWidth(),
        label = { Text("Type new name (leave blank for default)") },
        supportingText = { Text(supportingText) }
    )
    Spacer(Modifier.height(4.dp))
}

@Composable
fun ConfigSwitchItem(
    title: String,
    infoText: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
        Spacer(Modifier.height(4.dp))
    }
}

@Composable
fun ConfigButtonItem(
    title: String,
    infoText: String,
    primaryText: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    TitleWithInfo(title = title, infoText = infoText)
    Spacer(Modifier.height(8.dp))
    Text(primaryText)
    Spacer(Modifier.height(8.dp))
    TextButton(onClick = onClick, enabled = enabled) { Text(buttonText) }
}