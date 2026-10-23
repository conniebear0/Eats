package com.eats.app.ui.theme

import android.content.Context

/** The look the user picked in Profile > Appearance. [System] follows the phone's setting. */
enum class ThemeMode(val label: String) {
    System("Match my phone"),
    Light("Light (orange)"),
    Dark("Dark (purple)"),
}

/** Remembers the chosen [ThemeMode] between launches. */
class ThemeStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("eats_prefs", Context.MODE_PRIVATE)

    fun load(): ThemeMode =
        ThemeMode.entries.firstOrNull { it.name == prefs.getString("theme", null) } ?: ThemeMode.System

    fun save(mode: ThemeMode) = prefs.edit().putString("theme", mode.name).apply()
}
