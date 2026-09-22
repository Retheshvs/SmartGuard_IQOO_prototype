package com.smartguard.prototype.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import com.smartguard.prototype.session.AppMode

val AdultDarkColorScheme = darkColorScheme(
    primary          = AdultPrimary,
    primaryContainer = AdultPrimaryContainer,
    secondary        = AdultSecondary,
    background       = AdultBackground,
    surface          = AdultSurface,
    surfaceVariant   = AdultSurfaceVariant,
    onPrimary        = AdultOnPrimary,
    onBackground     = AdultOnBackground,
    onSurface        = AdultOnSurface,
    outline          = AdultOutline,
    error            = ErrorColor
)

val ChildDarkColorScheme = darkColorScheme(
    primary             = ChildPrimary,
    primaryContainer    = ChildPrimaryContainer,
    secondary           = ChildSecondary,
    secondaryContainer  = ChildSecondaryContainer,
    background          = ChildBackground,
    surface             = ChildSurface,
    surfaceVariant      = ChildSurfaceVariant,
    onPrimary           = ChildOnPrimary,
    onBackground        = ChildOnBackground,
    onSurface           = ChildOnSurface,
    outline             = ChildOutline,
    error               = ErrorColor
)

val SafeDarkColorScheme = darkColorScheme(
    primary          = SafePrimary,
    primaryContainer = SafePrimaryContainer,
    background       = SafeBackground,
    surface          = SafeSurface,
    onPrimary        = SafeOnPrimary,
    onBackground     = SafeOnBackground,
    error            = ErrorColor
)

val LockedDarkColorScheme = darkColorScheme(
    primary      = DefaultPrimary,
    background   = DefaultBackground,
    surface      = DefaultSurface,
    onBackground = DefaultOnBackground,
    error        = ErrorColor
)

/**
 * Root theme composable.
 *
 * Switches color scheme based on [AppMode] so the visual difference between
 * Adult ↔ Child ↔ Safe is unmistakable even from a few meters away on a demo stage.
 * The theme change propagates to every composable that reads [MaterialTheme.colorScheme].
 */
@Composable
fun SmartGuardTheme(
    mode: AppMode = AppMode.LOCKED,
    content: @Composable () -> Unit
) {
    val colorScheme = when (mode) {
        AppMode.ADULT  -> AdultDarkColorScheme
        AppMode.CHILD  -> ChildDarkColorScheme
        AppMode.TEEN   -> ChildDarkColorScheme // Reuse Child for Teen in prototype
        AppMode.SAFE   -> SafeDarkColorScheme
        AppMode.LOCKED -> LockedDarkColorScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography  = SmartGuardTypography,
        content     = content
    )
}
