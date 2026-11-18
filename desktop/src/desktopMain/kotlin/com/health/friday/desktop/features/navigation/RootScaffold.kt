package com.health.friday.desktop.features.navigation

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import cafe.adriel.voyager.navigator.tab.CurrentTab
import cafe.adriel.voyager.navigator.tab.LocalTabNavigator
import cafe.adriel.voyager.navigator.tab.TabNavigator

@Composable
fun RootScaffold() {
    TabNavigator(DashboardTab) { navigator ->
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
        ) {
            FridayNavigationRail()
            
            Box(modifier = Modifier.weight(1f).fillMaxSize()) {
                // Smooth crossfade between tabs instead of instant snapping
                Crossfade(
                    targetState = navigator.current,
                    animationSpec = tween(durationMillis = 300)
                ) { _ ->
                    CurrentTab()
                }
            }
        }
    }
}

@Composable
private fun FridayNavigationRail() {
    val tabNavigator = LocalTabNavigator.current
    val tabs = listOf(DashboardTab, DevicesTab, SettingsTab)

    NavigationRail(
        containerColor = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface
    ) {
        tabs.forEach { tab ->
            val selected = tabNavigator.current.key == tab.key
            NavigationRailItem(
                selected = selected,
                onClick = { tabNavigator.current = tab },
                icon = { 
                    tab.options.icon?.let { Icon(painter = it, contentDescription = tab.options.title) } 
                },
                label = { Text(tab.options.title) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.primary,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f),
                    unselectedTextColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                )
            )
        }
    }
}
