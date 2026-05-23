package com.example.ava.ui.screens.settings.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * Container for a lazy list of settings that applies a fixed padding to each item.
 */
@Composable
fun SettingsList(
    innerPadding: PaddingValues,
    content: LazyListScope.() -> Unit
) = LazyColumn(
    modifier = Modifier
        .fillMaxSize()
        .padding(innerPadding),
    contentPadding = PaddingValues(16.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
    content = content
)

/**
 * A title for a section of settings.
 */
@Composable
fun SectionTitle(title: String) = Text(
    text = title,
    color = MaterialTheme.colorScheme.primary,
    style = MaterialTheme.typography.titleSmall
)