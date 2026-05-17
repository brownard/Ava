package com.example.ava.ui.screens

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults.topAppBarColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.navigation.NavController
import com.example.ava.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackNavigationScreen(
    navController: NavController,
    title: String,
    topBarActions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = { BackNavigationBar(navController, title, topBarActions) },
        content = content
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackNavigationBar(
    navController: NavController,
    title: String,
    actions: @Composable RowScope.() -> Unit = {}
) = TopAppBar(
    colors = topAppBarColors(
        containerColor = MaterialTheme.colorScheme.primaryContainer,
        titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
    ),
    title = {
        Text(title)
    },
    navigationIcon = {
        IconButton(
            onClick = { navController.popBackStack() }
        ) {
            Icon(
                painter = painterResource(R.drawable.arrow_back_24px),
                contentDescription = "Back",
                tint = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    },
    actions = actions
)