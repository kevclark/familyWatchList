package org.seg7.familywatchlist.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised
import org.seg7.familywatchlist.ui.theme.OnAccent

/**
 * The app's one selectable pill — search's media-type chips, history's profile filter, the
 * age-rating cap selector, the log-watch sheet's profile chips.
 *
 * Replaces Material 3's `FilterChip`, which was a big part of why M2a read as "a form": its
 * default look is an outlined rounded rect with a leading tick, at Material's sizing, in
 * Material's colours. This is the same behaviour in §5a's palette — filled accent when
 * selected, a hairline outline when not — with an optional [leading] slot so a profile chip can
 * carry an avatar.
 */
@Composable
fun FilterPill(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    val background by animateColorAsState(
        targetValue = if (selected) Accent else Color.Transparent,
        label = "pill-bg",
    )
    val contentColor by animateColorAsState(
        targetValue = if (selected) OnAccent else ChalkMuted,
        label = "pill-fg",
    )
    val interactionSource = remember { MutableInteractionSource() }

    Row(
        modifier = modifier
            .clip(MaterialTheme.shapes.extraLarge)
            .background(if (selected) background else InkRaised)
            .border(
                width = 1.dp,
                color = if (selected) Accent else InkHairline,
                shape = MaterialTheme.shapes.extraLarge,
            )
            .clickableNoRipple(interactionSource, onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
            .semantics {
                this.selected = selected
                contentDescription = label
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        leading?.invoke()
        Text(text = label, style = MaterialTheme.typography.labelLarge, color = contentColor)
    }
}
