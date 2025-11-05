package com.example.ava.ui.screens.settings.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.ava.R
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DialogScope.ActionDialog(
    title: String = "",
    description: String = "",
    confirmEnabled: Boolean = true,
    onDismissRequest: () -> Unit = {},
    onConfirmRequest: () -> Unit = {},
    content: @Composable () -> Unit = {}
) {
    BasicAlertDialog(
        onDismissRequest = onDismissRequest
    ) {
        Surface(
            modifier = Modifier
                .wrapContentWidth()
                .wrapContentHeight(),
            shape = MaterialTheme.shapes.large,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = title,
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(2.dp))
                if (description.isNotBlank()) {
                    Text(text = description)
                    Spacer(modifier = Modifier.height(2.dp))
                }
                Box(modifier = Modifier.weight(weight = 1f, fill = false)) {
                    content()
                }
                Spacer(modifier = Modifier.height(2.dp))
                Row(
                    modifier = Modifier.align(Alignment.End)
                ) {
                    TextButton(
                        onClick = {
                            onDismissRequest()
                            closeDialog()
                        }
                    ) {
                        Text(stringResource(R.string.label_cancel))
                    }
                    TextButton(
                        enabled = confirmEnabled,
                        onClick = {
                            onConfirmRequest()
                            closeDialog()
                        }
                    ) {
                        Text(stringResource(R.string.label_ok))
                    }
                }
            }
        }
    }
}

@Stable
class DialogScope {
    private val _isDialogOpen = MutableStateFlow(false)
    val isDialogOpen get() = _isDialogOpen.asStateFlow()

    fun openDialog() {
        _isDialogOpen.value = true
    }

    fun closeDialog() {
        _isDialogOpen.value = false
    }
}