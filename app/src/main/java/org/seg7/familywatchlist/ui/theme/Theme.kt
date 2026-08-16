package org.seg7.familywatchlist.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

private val DarkColors = darkColorScheme(
    primary = PopcornGold,
    onPrimary = PopcornGoldDark,
    secondary = DeepIndigoLight,
    background = Charcoal,
    onBackground = Cream,
    surface = Charcoal,
    onSurface = Cream,
    surfaceVariant = CharcoalRaised,
    onSurfaceVariant = SlateGrey,
)

private val LightColors = lightColorScheme(
    primary = PopcornGoldDark,
    onPrimary = Cream,
    secondary = DeepIndigo,
    background = Cream,
    onBackground = Charcoal,
    surface = Cream,
    onSurface = Charcoal,
    onSurfaceVariant = SlateGrey,
)

/**
 * App theme. Dark by default (PLAN.md §5), with Material You dynamic colour on Android 12+.
 */
@Composable
fun FamilyWatchListTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = true,
    content: @Composable () -> Unit,
) {
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            val context = LocalContext.current
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }

        darkTheme -> DarkColors
        else -> LightColors
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        content = content,
    )
}
