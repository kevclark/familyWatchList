package org.seg7.familywatchlist.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk

/**
 * A single avatar tile, rendering whichever [AvatarStyle] the option carries.
 *
 * PLAN.md §5a moves profile tiles from circles to **rounded squares** — that's the shape every
 * streaming service uses for profiles, and circles are what made M2a's picker read as a
 * contacts list. [name] supplies the letter for [AvatarStyle.INITIAL]; it's optional so the
 * picker grid (where there's no name yet) can render the same component with a neutral glyph.
 */
@Composable
fun AvatarBadge(
    option: AvatarOption,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    selected: Boolean = false,
    name: String? = null,
) {
    val shape = androidx.compose.foundation.shape.RoundedCornerShape(size * 0.22f)
    Box(
        modifier = modifier
            .size(size)
            .clip(shape)
            .background(option.color)
            .then(if (selected) Modifier.border(2.5.dp, Accent, shape) else Modifier),
        contentAlignment = Alignment.Center,
    ) {
        when (option.style) {
            AvatarStyle.EMOJI -> Text(
                text = option.emoji,
                fontSize = (size.value * 0.46).sp,
                textAlign = TextAlign.Center,
            )

            AvatarStyle.INITIAL -> Text(
                text = name?.trim()?.firstOrNull()?.uppercase() ?: "A",
                fontSize = (size.value * 0.42).sp,
                fontWeight = FontWeight.Bold,
                color = Chalk,
                textAlign = TextAlign.Center,
            )

            AvatarStyle.SOLID -> Unit
        }
    }
}

/** Grid of [AVATAR_PRESETS] for onboarding's first-profile step and the profile add/edit dialog. */
@Composable
fun AvatarPickerGrid(
    selected: AvatarOption,
    onSelect: (AvatarOption) -> Unit,
    modifier: Modifier = Modifier,
    name: String? = null,
) {
    LazyVerticalGrid(columns = GridCells.Fixed(6), modifier = modifier) {
        items(AVATAR_PRESETS) { option ->
            val isSelected = option == selected
            Box(
                modifier = Modifier
                    .size(52.dp)
                    .semantics {
                        contentDescription = when (option.style) {
                            AvatarStyle.EMOJI -> "Avatar ${option.emoji}"
                            AvatarStyle.INITIAL -> "Initial avatar"
                            AvatarStyle.SOLID -> "Plain avatar"
                        }
                        this.selected = isSelected
                    }
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                AvatarBadge(option = option, size = 42.dp, selected = isSelected, name = name)
            }
        }
    }
}
