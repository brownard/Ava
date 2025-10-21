package com.example.ava.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment.Companion.CenterHorizontally
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ava.R
import kotlinx.coroutines.launch
import kotlin.collections.forEach
import kotlin.text.toIntOrNull

@Composable
fun VoiceSatelliteSettingsForm(modifier: Modifier = Modifier, viewModel: SettingsViewModel = viewModel()) {
    Column(
        horizontalAlignment = CenterHorizontally
    ) {
        val coroutineScope = rememberCoroutineScope()
        val settingsState by viewModel.validationState.collectAsStateWithLifecycle(SettingsState())

        LaunchedEffect(viewModel) {
            viewModel.loadSettings()
        }

        ValidatedTextField(
            state = viewModel.nameTextState,
            label = stringResource(R.string.label_voice_satellite_name),
            isValid = settingsState.name.isValid,
            validationText = settingsState.name.validation
        )

        ValidatedTextField(
            state = viewModel.portTextState,
            label = stringResource(R.string.label_voice_satellite_port),
            isValid = settingsState.port.isValid,
            validationText = settingsState.port.validation,
            inputTransformation = intOrEmptyInputTransformation
        )

        ValidatedDropdownMenu(
            state = viewModel.wakeWordTextState,
            options = viewModel.wakeWords.collectAsStateWithLifecycle(listOf()).value,
            label = stringResource(R.string.label_voice_satellite_wake_word),
            isValid = settingsState.wakeWord.isValid,
            validationText = settingsState.wakeWord.validation
        )

        Button(
            content = { Text("Save") },
            onClick = {
                coroutineScope.launch {
                    viewModel.saveSettings()
                }
            }
        )
    }
}

@Composable
fun ValidatedTextField(
    state: TextFieldState,
    label: String = "",
    isValid: Boolean = true,
    validationText: String = "",
    inputTransformation: InputTransformation? = null
) {
    TextField(
        state = state,
        label = {
            Text(text = label)
        },
        isError = !isValid,
        supportingText = {
            Text(
                text = validationText
            )
        },
        inputTransformation = inputTransformation
    )
}

val intOrEmptyInputTransformation: InputTransformation = InputTransformation {
    val text = toString()
    if (text.length > 0) {
        val value = text.toIntOrNull()
        if (value == null)
            revertAllChanges()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ValidatedDropdownMenu(
    state: TextFieldState,
    options: Iterable<String>,
    label: String = "",
    isValid: Boolean = true,
    validationText: String = "",
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        TextField(
            // The `menuAnchor` modifier must be passed to the text field to handle
            // expanding/collapsing the menu on click. A read-only text field has
            // the anchor type `PrimaryNotEditable`.
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable),
            state = state,
            readOnly = true,
            lineLimits = TextFieldLineLimits.SingleLine,
            label = { Text(label) },
            isError = !isValid,
            supportingText = {
                Text(
                    text = validationText
                )
            },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            colors = ExposedDropdownMenuDefaults.textFieldColors(),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option, style = MaterialTheme.typography.bodyLarge) },
                    onClick = {
                        state.setTextAndPlaceCursorAtEnd(option)
                        expanded = false
                    },
                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding,
                )
            }
        }
    }
}