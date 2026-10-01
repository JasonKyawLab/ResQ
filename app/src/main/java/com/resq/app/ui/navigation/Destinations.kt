package com.resq.app.ui.navigation

/** The app's three top-level screens. A new screen is one new entry + one new composable route. */
enum class ResQDestination(val route: String) {
    SENDER("sender"),
    RECEIVER("receiver"),
    SETTINGS("settings"),
}
