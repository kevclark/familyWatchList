package org.seg7.familywatchlist.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp

/**
 * PLAN.md §5a: one fixed near-black scheme with one confident [Accent].
 *
 * **Deliberate divergence from §5's earlier "Material 3 dynamic colour with dark default":**
 * dynamic colour hands the accent (and the surface tints) to whatever wallpaper the device has,
 * which directly contradicts §5a's newer, more specific "one confident accent colour used
 * sparingly". §5a was written after Kev's review, so it wins — dynamic colour is off, and the
 * app looks the same on every device. Flagged for Kev in the M2b report.
 *
 * There is also no light scheme any more. A streaming app is dark-first by identity (§5:
 * "edge-to-edge, dark-first"), and maintaining a second palette that nobody would ever choose
 * is cost without benefit; the theme ignores the system light/dark setting on purpose.
 */
private val AppColors = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    primaryContainer = Accent,
    onPrimaryContainer = OnAccent,
    secondary = Accent,
    onSecondary = OnAccent,
    background = Ink,
    onBackground = Chalk,
    surface = Ink,
    onSurface = Chalk,
    // Only ever used for genuinely image-free surfaces (sheets, fields) — §5a bans boxed
    // backgrounds anywhere imagery is present.
    surfaceVariant = InkRaised,
    onSurfaceVariant = ChalkMuted,
    surfaceContainer = InkRaised,
    surfaceContainerHigh = InkRaised,
    surfaceContainerHighest = InkRaised,
    surfaceContainerLow = Ink,
    surfaceContainerLowest = Ink,
    outline = InkHairline,
    outlineVariant = InkHairline,
    error = Crimson,
    onError = OnAccent,
    scrim = Ink,
)

/** Squarer than Material's default — posters are near-square-cornered on Netflix/Prime. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(3.dp),
    small = RoundedCornerShape(5.dp),
    medium = RoundedCornerShape(8.dp),
    large = RoundedCornerShape(14.dp),
    extraLarge = RoundedCornerShape(22.dp),
)

@Composable
fun FamilyWatchListTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
