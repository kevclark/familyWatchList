package org.seg7.familywatchlist.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed

/**
 * Click with no Material ripple.
 *
 * PLAN.md §5a asks for "subtle scale-on-press for poster cards" — a ripple *on top of* poster
 * art fights that: it washes a grey circle across the image and reads as a Material form
 * control, which is exactly the "primary school" quality Kev flagged. Every tappable surface in
 * the app that sits on imagery, or that draws its own pressed/selected state, uses this instead
 * of a plain `clickable`.
 *
 * This overload remembers its own interaction source, for the common case where the caller only
 * wants the click and doesn't animate off the press.
 */
fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier = composed {
    clickableNoRipple(remember { MutableInteractionSource() }, onClick)
}

/**
 * As above, but driven by a caller-supplied interaction source — for the surfaces that *do*
 * animate on press (poster cards scale down) and need the same press signal the click uses.
 */
fun Modifier.clickableNoRipple(
    interactionSource: MutableInteractionSource,
    onClick: () -> Unit,
): Modifier = this.clickable(
    interactionSource = interactionSource,
    indication = null,
    onClick = onClick,
)
