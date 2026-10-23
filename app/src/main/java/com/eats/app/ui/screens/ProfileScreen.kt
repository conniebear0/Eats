package com.eats.app.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.eats.app.auth.AuthViewModel
import com.eats.app.auth.User
import com.eats.app.order.OrderViewModel
import com.eats.app.ui.components.AddressDialog
import com.eats.app.ui.components.Panel
import com.eats.app.ui.components.ScreenHeader
import com.eats.app.ui.theme.ThemeMode

/** Profile tab: who is signed in, the saved delivery address, shortcuts and sign out. */
@Composable
fun ProfileScreen(
    auth: AuthViewModel,
    orders: OrderViewModel,
    themeMode: ThemeMode = ThemeMode.System,
    onThemeChange: (ThemeMode) -> Unit = {},
    onOpenFavourites: () -> Unit,
    onHelp: () -> Unit,
    onSignedOut: () -> Unit,
    onMessage: (String) -> Unit,
    onComplaints: () -> Unit = {},
    openComplaints: Int = 0,
) {
    val user by auth.user.collectAsStateWithLifecycle()
    val state by orders.state.collectAsStateWithLifecycle()
    var editAddress by rememberSaveable { mutableStateOf(false) }
    var pickTheme by rememberSaveable { mutableStateOf(false) }
    var confirmSignOut by rememberSaveable { mutableStateOf(false) }

    Column(Modifier.fillMaxSize()) {
        ScreenHeader(title = "Profile", onBack = null)
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            user?.let { UserCard(it) }

            Panel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocationOn, null, tint = MaterialTheme.colorScheme.tertiary)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Delivery address", style = MaterialTheme.typography.titleMedium)
                        Text(
                            state.address.ifBlank { "No address saved yet" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { editAddress = true }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit delivery address")
                    }
                }
            }

            Panel {
                MenuRow(Icons.Filled.Favorite, "Favourites", "${state.favourites.size} saved", onOpenFavourites)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(Icons.Filled.Settings, "Appearance", themeMode.label) { pickTheme = true }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(Icons.Filled.Info, "Help and support", "Chat with us about an order", onHelp)
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(
                    Icons.Filled.Warning,
                    "My complaints",
                    if (openComplaints == 0) "Follow anything you've reported" else "$openComplaints open",
                    onComplaints,
                )
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                MenuRow(Icons.AutoMirrored.Filled.ExitToApp, "Sign out", null) { confirmSignOut = true }
            }

            Spacer(Modifier.height(8.dp))
            Text(
                "eats 1.0",
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(12.dp))
        }
    }

    if (editAddress) {
        AddressDialog(
            current = state.address,
            onSave = {
                orders.setAddress(it)
                editAddress = false
                onMessage("Delivery address saved")
            },
            onDismiss = { editAddress = false },
        )
    }

    if (pickTheme) {
        AlertDialog(
            onDismissRequest = { pickTheme = false },
            title = { Text("Appearance") },
            text = {
                Column {
                    ThemeMode.entries.forEach { option ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable {
                                    onThemeChange(option)
                                    pickTheme = false
                                }
                                .padding(vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            RadioButton(selected = option == themeMode, onClick = null)
                            Text(option.label, Modifier.padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { pickTheme = false }) { Text("Close") } },
        )
    }

    if (confirmSignOut) {
        AlertDialog(
            onDismissRequest = { confirmSignOut = false },
            title = { Text("Sign out?") },
            text = { Text("Your cart will be cleared. Your address, favourites and past orders stay saved on this device.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmSignOut = false
                    auth.signOut()
                    onSignedOut()
                }) { Text("Sign out") }
            },
            dismissButton = { TextButton(onClick = { confirmSignOut = false }) { Text("Stay signed in") } },
        )
    }
}

@Composable
private fun UserCard(user: User) {
    Row(Modifier.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(64.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    user.initials,
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onPrimary,
                )
            }
        }
        Column(Modifier.padding(start = 16.dp)) {
            Text(user.fullName, style = MaterialTheme.typography.titleLarge)
            Text(user.email, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatMobile(user.mobile), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, title: String, subtitle: String?, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.tertiary)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "821234567" -> "+27 82 123 4567" */
private fun formatMobile(digits: String): String =
    if (digits.length == 9) "+27 ${digits.take(2)} ${digits.substring(2, 5)} ${digits.substring(5)}" else "+27 $digits"
