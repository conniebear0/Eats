package com.eats.app

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.eats.app.ui.EatsApp
import com.eats.app.ui.theme.EatsTheme
import com.eats.app.ui.theme.ThemeMode
import com.eats.app.ui.theme.ThemeStore

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        // Shows the system splash (white + plate in light mode, black + plate in dark mode),
        // then hands over to the animated Compose splash.
        installSplashScreen()
        super.onCreate(savedInstanceState)
        val themeStore = ThemeStore(this)
        setContent {
            // The user's choice from Profile > Appearance, saved between launches.
            var mode by remember { mutableStateOf(themeStore.load()) }
            val dark = when (mode) {
                ThemeMode.System -> isSystemInDarkTheme()
                ThemeMode.Light -> false
                ThemeMode.Dark -> true
            }
            // Keep status/navigation bar icons readable when the app theme differs from the phone's.
            DisposableEffect(dark) {
                enableEdgeToEdge(
                    statusBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                    navigationBarStyle = SystemBarStyle.auto(Color.TRANSPARENT, Color.TRANSPARENT) { dark },
                )
                onDispose {}
            }
            EatsTheme(darkTheme = dark) {
                EatsApp(
                    themeMode = mode,
                    onThemeChange = {
                        mode = it
                        themeStore.save(it)
                    },
                )
            }
        }
    }
}
