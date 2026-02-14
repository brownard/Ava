package com.example.ava.ui.screens.home

import androidx.lifecycle.ViewModel
import com.example.ava.settings.DisplaySettingsStore
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class HomeViewModel @Inject constructor(
    displaySettingsStore: DisplaySettingsStore
) : ViewModel() {
    val displaySettings = displaySettingsStore.getFlow()
}
