package org.seg7.familywatchlist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import org.seg7.familywatchlist.ui.theme.Ink

/**
 * PLAN.md §5a: "Surfaces gain depth from gradient scrims over imagery (dark-to-transparent
 * behind titles on hero/backdrop images), not flat elevated cards."
 *
 * Two scrims, layered over the same hero image:
 *  - [BottomScrim] fades the ground colour up from the bottom so title/metadata text sits on
 *    solid contrast and the image dissolves into the page rather than ending at a hard edge.
 *  - [TopScrim] darkens the top strip so a transparent app bar's icons stay legible over
 *    whatever happens to be in the image (§5a: "a transparent/scrim top bar over hero content,
 *    not a solid Material app bar").
 *
 * Stops are non-linear on purpose — a straight two-stop gradient greys out the middle of the
 * image; holding transparent for the first stretch keeps the art clean and does the darkening
 * only where text actually lands.
 */
@Composable
fun BottomScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Color.Transparent,
                    0.45f to Ink.copy(alpha = 0.25f),
                    0.72f to Ink.copy(alpha = 0.78f),
                    1.0f to Ink,
                )
            )
    )
}

@Composable
fun TopScrim(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    0.0f to Ink.copy(alpha = 0.72f),
                    0.55f to Ink.copy(alpha = 0.18f),
                    1.0f to Color.Transparent,
                )
            )
    )
}
