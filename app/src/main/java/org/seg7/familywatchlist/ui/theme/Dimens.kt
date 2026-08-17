package org.seg7.familywatchlist.ui.theme

import androidx.compose.ui.unit.dp

/**
 * PLAN.md §5a layout tokens, in one place so every carousel/grid in the app lines up instead
 * of each screen inventing its own numbers (which is what made M2a look inconsistent).
 */
object Dimens {
    /** Screen gutter. Carousels bleed past this via `contentPadding`, they don't inset. */
    val Gutter = 16.dp

    /** §5a "tight inter-card spacing" — deliberately smaller than Material's 16dp default. */
    val CardGap = 6.dp

    /** Vertical rhythm between stacked carousels in Home's single continuous feed. */
    val RowGap = 26.dp

    /** Standard poster width in a carousel; height follows the 2:3 ratio (→ 168dp). */
    val PosterWidth = 112.dp

    /** Larger poster for the search results grid (3 columns on a phone). */
    val PosterWidthLarge = 128.dp

    /** 2:3 is the TMDB poster aspect ratio (w342 assets are 342×513). */
    const val PosterAspect = 2f / 3f

    /** Backdrop hero on Home; 16:9 would crop the title text off, 4:3 wastes the fold. */
    const val HeroAspect = 3f / 4f

    /** 16:9 backdrop on the title-details hero. */
    const val BackdropAspect = 16f / 10f
}
