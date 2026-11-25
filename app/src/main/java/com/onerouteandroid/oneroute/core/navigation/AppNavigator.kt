package com.onerouteandroid.oneroute.core.navigation

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.onerouteandroid.oneroute.core.components.MainTab

class AppNavigator(start: Route = Route.Splash) {
    private val stack = mutableStateListOf(start)

    var entryId by mutableIntStateOf(0)
        private set
    var lastWasForward by mutableStateOf(true)
        private set

    val current: Route get() = stack.last()
    val canGoBack: Boolean get() = stack.size > 1
    val depth: Int get() = stack.size

    fun navigate(route: Route) {
        stack.add(route)
        mark(forward = true)
    }

    fun back(): Boolean {
        if (!canGoBack) return false
        stack.removeAt(stack.lastIndex)
        mark(forward = false)
        return true
    }

    fun resetTo(route: Route) {
        stack.clear()
        stack.add(route)
        mark(forward = true)
    }

    fun selectTab(tab: MainTab) {
        val target = when (tab) {
            MainTab.INICIO -> Route.Home
            MainTab.BUSCAR -> Route.Search
            MainTab.MIS_VIAJES -> Route.MyTrips()
            MainTab.PERFIL -> Route.Profile
        }
        if (current == target) return
        stack.clear()
        stack.add(Route.Home)
        if (target != Route.Home) stack.add(target)
        mark(forward = true)
    }

    private fun mark(forward: Boolean) {
        lastWasForward = forward
        entryId++
    }
}
