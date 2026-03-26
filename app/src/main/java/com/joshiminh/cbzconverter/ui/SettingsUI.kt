package com.joshiminh.cbzconverter.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

@Composable
fun SectionCard(title: String, modifier: Modifier = Modifier, iconResId: Int? = null, action: (@Composable () -> Unit)? = null, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(modifier = modifier.fillMaxWidth(), elevation = CardDefaults.elevatedCardElevation()) {
        Column(Modifier.padding(12.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                iconResId?.let { Image(painter = painterResource(id = it), contentDescription = null, modifier = Modifier.size(24.dp).padding(end = 8.dp)) }
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                action?.invoke()
            }
            Spacer(Modifier.height(12.dp))
            content()
        }
    }
}

@Composable
fun ConfigNumberItem(title: String, infoText: String, value: String, enabled: Boolean, onValidNumber: (String) -> Unit) {
    var text by remember(value) { mutableStateOf(value) }
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(infoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(value = text, onValueChange = { if (it.isEmpty() || it.all { c -> c.isDigit() }) text = it }, modifier = Modifier.fillMaxWidth(), enabled = enabled, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), trailingIcon = { TextButton(onClick = { onValidNumber(text) }, enabled = enabled && text.isNotBlank()) { Text("Save") } })
    }
}

@Composable
fun ConfigSwitchItem(title: String, infoText: String, checked: Boolean, enabled: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(infoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
fun ConfigButtonItem(title: String, infoText: String, primaryText: String, buttonText: String, enabled: Boolean, onClick: () -> Unit) {
    Column(Modifier.fillMaxWidth()) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        Text(infoText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(4.dp))
        Text(primaryText, style = MaterialTheme.typography.bodyMedium)
        Button(onClick = onClick, modifier = Modifier.fillMaxWidth(), enabled = enabled) { Text(buttonText) }
    }
}

@Composable
fun Spacer12Divider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f))
    Spacer(Modifier.height(12.dp))
}