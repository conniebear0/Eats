package com.eats.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Colours the Material scheme has no slot for. They follow the theme that is actually showing,
// so they also work when EatsTheme(darkTheme = ...) is forced in a preview.

val StarYellow = Color(0xFFF59E0B)
val HeartRed = Color(0xFFE11D48)

@Composable
fun isDarkSurface(): Boolean = MaterialTheme.colorScheme.background.luminance() < 0.5f

/** Face of the plate: orange in light mode, light purple in dark mode (matches the logo). */
@Composable
fun plateFace(): Color = if (isDarkSurface()) Purple400 else Orange400

@Composable
fun successColor(): Color = if (isDarkSurface()) Color(0xFF4ADE80) else Color(0xFF15803D)
