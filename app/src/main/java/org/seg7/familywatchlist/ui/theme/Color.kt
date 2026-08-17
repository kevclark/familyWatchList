package org.seg7.familywatchlist.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * PLAN.md §5a colour tokens. Replaces M2a's Material-default-ish dark grey palette, which read
 * as "a bit dull" in Kev's review.
 *
 * ## Ground
 * Near-black, deliberately *below* Material's default dark surface (`#1C1B1F`) so poster art is
 * the brightest thing on screen — the whole point of §5a's "imagery-to-text ratio should
 * visibly favour imagery". [Ink] is the page ground; [InkRaised] is used only where a surface
 * genuinely has no imagery on it (sheets, dialogs, text fields), never as a card behind a
 * poster.
 */
val Ink = Color(0xFF0B0B0D)
val InkRaised = Color(0xFF141417)
val InkHairline = Color(0xFF232327)

/** Text ramp — one bright, one muted, one faint. Pure white is avoided; it glares on near-black. */
val Chalk = Color(0xFFF2F2F4)
val ChalkMuted = Color(0xFFA0A0AA)
val ChalkFaint = Color(0xFF6A6A74)

/**
 * ## Accent candidates (PLAN.md §5a: "one confident accent colour … feature-builder proposes
 * 2–3 candidate swatches and Kev picks")
 *
 * All three are deliberately away from Netflix red (`#E50914`) and Disney blue (`#0063E5`), and
 * all three clear WCAG AA (4.5:1) against [Ink] for text and pass 3:1 for UI/graphical elements,
 * so any of them can be swapped in without a legibility re-check.
 *
 * - [AccentEmber]  — warm amber-coral. Cinema-marquee warmth, closest in spirit to the app's
 *                    "family movie night" subject; reads as premium rather than alarming.
 * - [AccentAurora] — cool mint-teal. The most distinctive of the three (nobody in UK streaming
 *                    owns it), and the strongest "tech product" signal.
 * - [AccentOrchid] — electric violet. Splits the difference: rich and modern, and the one that
 *                    sits most comfortably next to varied poster art without clashing.
 *
 * **[Accent] is the single switch.** Point it at a different candidate and the whole app
 * follows — nothing else in the codebase names a candidate directly.
 */
val AccentEmber = Color(0xFFFF7A45)
val AccentAurora = Color(0xFF3ADFB0)
val AccentOrchid = Color(0xFFA779FF)

/** Currently selected accent — Kev's pick swaps this one line. Defaults to Ember. */
val Accent = AccentEmber

/** Text/icon colour to place *on top of* [Accent]-filled surfaces. */
val OnAccent = Color(0xFF170B05)

/** Destructive actions (delete an event, remove from list). */
val Crimson = Color(0xFFFF5A5A)

/**
 * PLAN.md §5a avatars: "pull the 12-swatch palette in toward restraint, not primary-colour
 * brightness." These are desaturated, mid-dark tiles that sit calmly on [Ink] instead of the
 * M2a set's saturated primaries.
 */
val AvatarSwatches: List<Color> = listOf(
    Color(0xFF7C5C4A), // clay
    Color(0xFF4A6357), // moss
    Color(0xFF4C5A78), // slate blue
    Color(0xFF6B5478), // plum
    Color(0xFF7A6A45), // brass
    Color(0xFF3F6B6B), // teal
    Color(0xFF7A4A55), // rosewood
    Color(0xFF55607A), // dusk
    Color(0xFF5E6B45), // olive
    Color(0xFF6E5A6E), // mauve
    Color(0xFF44586B), // steel
    Color(0xFF6B6B6B), // graphite
)
