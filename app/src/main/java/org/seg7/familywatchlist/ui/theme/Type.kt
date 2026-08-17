package org.seg7.familywatchlist.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.DeviceFontFamilyName
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * PLAN.md §5a typography: "bold/condensed display type for titles and section headers,
 * restrained body text."
 *
 * Condensed comes from the *device* font (`sans-serif-condensed`, present on every Android
 * build since well before minSdk 26) via [DeviceFontFamilyName], rather than shipping a
 * webfont — no APK weight, no licence question, and it degrades to the regular sans-serif on
 * any device that somehow lacks it. Judgment call worth flagging: a bundled variable font
 * (Archivo/Oswald) would be more visually distinctive and pixel-identical across devices; the
 * device family was chosen for M2b because it gets 90% of the effect for zero build cost.
 */
private val Condensed: FontFamily = FontFamily(
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Black),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Bold),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Medium),
    Font(DeviceFontFamilyName("sans-serif-condensed"), weight = FontWeight.Normal),
)

private val Body: FontFamily = FontFamily.SansSerif

/**
 * Display styles use [Condensed] at heavy weights with tightened tracking — the "streaming
 * service wordmark" feel. Body styles stay on the regular sans at normal weight so text never
 * competes with poster art.
 */
val AppTypography = Typography(
    displayLarge = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Black,
        fontSize = 44.sp,
        lineHeight = 46.sp,
        letterSpacing = (-1.0).sp,
    ),
    displayMedium = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Black,
        fontSize = 34.sp,
        lineHeight = 37.sp,
        letterSpacing = (-0.8).sp,
    ),
    displaySmall = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Black,
        fontSize = 27.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.5).sp,
    ),
    headlineMedium = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 25.sp,
        lineHeight = 29.sp,
        letterSpacing = (-0.4).sp,
    ),
    headlineSmall = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 21.sp,
        lineHeight = 25.sp,
        letterSpacing = (-0.3).sp,
    ),
    /** Carousel section headers — the most repeated "display" style in the app. */
    titleLarge = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        lineHeight = 23.sp,
        letterSpacing = (-0.2).sp,
    ),
    titleMedium = TextStyle(
        fontFamily = Condensed,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 20.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp,
        lineHeight = 18.sp,
        letterSpacing = 0.2.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 15.sp,
        letterSpacing = 0.3.sp,
    ),
    /** Small all-caps metadata (year · runtime · cert, "ADDED BY", provider kind). */
    labelSmall = TextStyle(
        fontFamily = Body,
        fontWeight = FontWeight.Medium,
        fontSize = 10.sp,
        lineHeight = 13.sp,
        letterSpacing = 0.9.sp,
    ),
)
