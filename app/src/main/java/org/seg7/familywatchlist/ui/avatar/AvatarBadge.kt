package org.seg7.familywatchlist.ui.avatar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** A single circular emoji-on-colour avatar, used for profile tiles, chips and the picker grid. */
@Composable
fun AvatarBadge(
    option: AvatarOption,
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    selected: Boolean = false,
) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(option.color)
            .then(
                if (selected) {
                    Modifier.border(3.dp, MaterialTheme.colorScheme.primary, CircleShape)
                } else {
                    Modifier
                },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = option.emoji, fontSize = (size.value * 0.5).sp, textAlign = TextAlign.Center)
    }
}

/** Grid of [AVATAR_PRESETS] for onboarding's first-profile step and the profile add/edit dialog. */
@Composable
fun AvatarPickerGrid(
    selected: AvatarOption,
    onSelect: (AvatarOption) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(4),
        modifier = modifier,
    ) {
        items(AVATAR_PRESETS) { option ->
            val isSelected = option.emoji == selected.emoji && option.color == selected.color
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .semantics {
                        contentDescription = "Avatar ${option.emoji}"
                        this.selected = isSelected
                    }
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                AvatarBadge(option = option, size = 56.dp, selected = isSelected)
            }
        }
    }
}
