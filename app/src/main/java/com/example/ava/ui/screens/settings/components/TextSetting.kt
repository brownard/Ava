package com.example.ava.ui.screens.settings.components

import androidx.compose.foundation.background
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldState
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.map

@Composable
fun TextSetting(
    name: String,
    description: String = "",
    value: String,
    enabled: Boolean = true,
    validation: ((String) -> String?)? = null,
    inputTransformation: InputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    onConfirmRequest: (String) -> Unit = {}
) {
    DialogSettingItem(
        name = name,
        description = description,
        value = value,
        enabled = enabled
    ) {
        TextDialog(
            title = name,
            description = description,
            value = value,
            onConfirmRequest = onConfirmRequest,
            validation = validation,
            inputTransformation = inputTransformation,
            keyboardOptions = keyboardOptions
        )
    }
}

@Composable
fun DialogScope.TextDialog(
    title: String = "",
    description: String = "",
    value: String = "",
    onConfirmRequest: (String) -> Unit,
    validation: ((String) -> String?)? = null,
    inputTransformation: InputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    val textFieldState = rememberTextFieldState(value)
    val validationState by snapshotFlow { textFieldState.text }
        .map { validation?.invoke(it.toString()) }
        .collectAsStateWithLifecycle(null)
    ActionDialog(
        title = title,
        description = description,
        confirmEnabled = validationState.isNullOrBlank(),
        onConfirmRequest = {
            onConfirmRequest(textFieldState.text.toString())
        }
    ) {
        ValidatedTextField(
            state = textFieldState,
            isValid = validationState.isNullOrBlank(),
            validationText = validationState ?: "",
            inputTransformation = inputTransformation,
            keyboardOptions = keyboardOptions
        )
    }
}

@Composable
fun ValidatedTextField(
    state: TextFieldState,
    label: String = "",
    isValid: Boolean = true,
    validationText: String = "",
    inputTransformation: InputTransformation? = null,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default
) {
    TextField(
        modifier = Modifier.background(color = Color.Transparent),
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
        inputTransformation = inputTransformation,
        keyboardOptions = keyboardOptions,
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
            errorContainerColor = Color.Transparent
        )
    )
}