package com.jd.softphone.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.jd.softphone.sip.CallPhase
import com.jd.softphone.ui.dialer.DialerScreen
import com.jd.softphone.ui.incall.InCallScreen
import com.jd.softphone.ui.settings.SettingsScreen

private enum class Tab(val route: String, val label: String) {
    Dialer("dialer", "Keypad"),
    Settings("settings", "Account"),
}

@Composable
fun SoftphoneRoot(viewModel: MainViewModel = hiltViewModel()) {
    val callState by viewModel.call.collectAsStateWithLifecycle()

    // An active call takes over the whole screen.
    if (callState.phase != CallPhase.IDLE && callState.phase != CallPhase.ENDED) {
        InCallScreen(
            state = callState,
            onAnswer = viewModel::answer,
            onHangUp = viewModel::hangUp,
            onToggleMute = viewModel::toggleMute,
            onToggleSpeaker = viewModel::toggleSpeaker,
            onDtmf = viewModel::sendDtmf,
        )
        return
    }

    val navController = rememberNavController()
    val registration by viewModel.registration.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            val backStack by navController.currentBackStackEntryAsState()
            val current = backStack?.destination
            NavigationBar {
                Tab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = current?.hierarchy?.any { it.route == tab.route } == true,
                        onClick = {
                            navController.navigate(tab.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = {
                            Icon(
                                imageVector = when (tab) {
                                    Tab.Dialer -> Icons.Default.Dialpad
                                    Tab.Settings -> Icons.Default.Settings
                                },
                                contentDescription = tab.label,
                            )
                        },
                        label = { Text(tab.label) },
                    )
                }
            }
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            NavHost(navController = navController, startDestination = Tab.Dialer.route) {
                composable(Tab.Dialer.route) {
                    DialerScreen(registration = registration)
                }
                composable(Tab.Settings.route) {
                    SettingsScreen()
                }
            }
        }
    }
}
