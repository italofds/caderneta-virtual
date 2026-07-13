package com.caderneta.virtual.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// ── Amber brand palette (matches the approved prototype) ──
private val Primary = Color(0xFF855400)
private val OnPrimary = Color(0xFFFFFFFF)
private val PrimaryContainer = Color(0xFFFFDDB4)
private val OnPrimaryContainer = Color(0xFF2A1700)
private val Secondary = Color(0xFF6B4300)
private val Surface = Color(0xFFFFF8F2)
private val OnSurface = Color(0xFF211A11)
private val SurfaceVariant = Color(0xFFF1E4D2)
private val OnSurfaceVariant = Color(0xFF50453A)
private val Outline = Color(0xFF837567)
private val OutlineVariant = Color(0xFFEFE2D0)
private val Error = Color(0xFFBA1A1A)

private val LightColors = lightColorScheme(
    primary = Primary,
    onPrimary = OnPrimary,
    primaryContainer = PrimaryContainer,
    onPrimaryContainer = OnPrimaryContainer,
    secondary = Secondary,
    onSecondary = OnPrimary,
    background = Surface,
    onBackground = OnSurface,
    surface = Surface,
    onSurface = OnSurface,
    surfaceVariant = SurfaceVariant,
    onSurfaceVariant = OnSurfaceVariant,
    outline = Outline,
    outlineVariant = OutlineVariant,
    error = Error,
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFF3BB6B),
    onPrimary = Color(0xFF472A00),
    primaryContainer = Color(0xFF6B4300),
    onPrimaryContainer = PrimaryContainer,
    background = Color(0xFF1A140D),
    surface = Color(0xFF1A140D),
    onSurface = Color(0xFFEDE0D2),
)

/** Per-vehicle accent colors used for the route line and list dots. */
val VehicleColors = listOf(
    Color(0xFF1A73E8), // matches the tweak the user chose in the prototype
    Color(0xFF6E7B23),
    Color(0xFFB26A00),
    Color(0xFF8E24AA),
    Color(0xFF00897B),
)

fun colorForDevice(address: String): Color =
    VehicleColors[(address.hashCode() and 0x7fffffff) % VehicleColors.size]

@Composable
fun CadernetaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = Typography(),
        content = content,
    )
}
