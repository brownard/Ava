package com.example.ava.ui.screens.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun SettingItem(
    modifier: Modifier = Modifier,
    name: String,
    description: String = "",
    value: String = "",
    action: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Details(name, description, value)
        ActionContainer {
            action()
        }
    }
}

@Composable
fun RowScope.Details(name: String, description: String = "", value: String = "") {
    Column(Modifier.weight(1f)) {
        Text(
            name,
            color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleMedium
        )
        if (description.isNotBlank()) {
            Text(description, style = MaterialTheme.typography.bodyMedium)
        }
        if (value.isNotBlank()) {
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
fun ActionContainer(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.padding(horizontal = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}