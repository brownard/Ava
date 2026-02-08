package com.example.ava.ui.screens.settings.components

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp

@Composable
fun <T> SelectSetting(
    name: String,
    description: String = "",
    selected: T?,
    items: List<T>?,
    enabled: Boolean = true,
    key: ((T) -> Any)? = null,
    value: (T?) -> String = { it.toString() },
    onConfirmRequest: (T?) -> Unit = {},
    onClearRequest: (() -> Unit)? = null
) {
    DialogSettingItem(
        name = name,
        description = description,
        value = value(selected),
        enabled = enabled,
        action = {
            if (onClearRequest != null && selected != null) {
                IconButton(onClick = onClearRequest) {
                    Icon(Icons.Default.Clear, contentDescription = "Clear")
                }
            }
        }
    ) {
        SelectDialog(
            title = name,
            description = description,
            selected = selected,
            items = items,
            key = key,
            value = value,
            onConfirmRequest = onConfirmRequest
        )
    }
}

@Composable
fun <T> DialogScope.SelectDialog(
    title: String = "",
    description: String = "",
    selected: T?,
    items: List<T>?,
    key: ((T) -> Any)? = null,
    value: (T) -> String = { it.toString() },
    onConfirmRequest: (T?) -> Unit,
) {
    var selectedItem by remember { mutableStateOf(selected) }
    ActionDialog(
        title = title,
        description = description,
        onConfirmRequest = {
            onConfirmRequest(selectedItem)
        }
    ) {
        if (items != null) {
            LazyColumn {
                items(
                    items = items,
                    key = key
                ) { item ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp)
                            .selectable(
                                selected = (item == selectedItem),
                                onClick = { selectedItem = item },
                                role = Role.RadioButton
                            )
                    ) {
                        RadioButton(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            selected = item == selectedItem,
                            onClick = null
                        )
                        Text(
                            text = value(item),
                            style = MaterialTheme.typography.titleMedium
                        )
                    }
                }
            }
        }
    }
}