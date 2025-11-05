package com.example.ava.ui

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ava.ui.screens.home.HomeScreen
import com.example.ava.ui.screens.settings.SettingsScreen
import kotlinx.serialization.Serializable

@Serializable
object Home

@Serializable
object Settings

@Composable
fun MainNavHost() {
    val navController = rememberNavController()
    NavHost(navController = navController, startDestination = Home) {
        composable<Home> {
            HomeScreen(navController)
        }
        composable<Settings> {
            SettingsScreen(navController)
        }
    }
}