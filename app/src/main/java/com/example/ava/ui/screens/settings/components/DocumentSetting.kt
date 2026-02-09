package com.example.ava.ui.screens.settings.components

import android.net.Uri
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.documentfile.provider.DocumentFile

@Composable
fun DocumentSetting(
    name: String,
    description: String = "",
    value: Uri?,
    mimeTypes: Array<String>,
    enabled: Boolean = true,
    onResult: (Uri?) -> Unit,
    onClearRequest: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val displayValue = remember(value) {
        if (value != null) DocumentFile.fromSingleUri(
            context,
            value
        )?.name else null
    }

    ActivityResultSetting(
        name = name,
        description = description,
        value = displayValue ?: "",
        enabled = enabled,
        contract = ActivityResultContracts.OpenDocument(),
        input = mimeTypes,
        onResult = onResult,
        onClearRequest = onClearRequest,
    )
}
