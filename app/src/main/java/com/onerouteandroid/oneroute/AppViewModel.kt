package com.onerouteandroid.oneroute

import androidx.lifecycle.ViewModel
import com.onerouteandroid.oneroute.core.navigation.AppNavigator
import com.onerouteandroid.oneroute.core.navigation.AppState

class AppViewModel : ViewModel() {
    val state = AppState()
    val navigator = AppNavigator()
}
