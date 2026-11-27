package com.oneroute

import androidx.lifecycle.ViewModel
import com.oneroute.core.navigation.AppNavigator
import com.oneroute.core.navigation.AppState

class AppViewModel : ViewModel() {
    val state = AppState()
    val navigator = AppNavigator()
}
