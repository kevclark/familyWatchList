package org.seg7.familywatchlist.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.data.recommend.MoreCardState
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * PLAN.md §5e (M16): the last item in a For You / Family Night row. Poster-shaped so it sits in the
 * rhythm of the row, raised ink with a hairline, no colour but [Accent] on the loading spinner.
 * "Show 30 more" over a muted "31–60 of 294"; while the next batch is being prepared the range
 * line gives way to a spinner and taps are ignored.
 */
@Composable
fun ShowMoreCard(state: MoreCardState, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = modifier
            .width(Dimens.PosterWidth)
            .aspectRatio(Dimens.PosterAspect)
            .clip(shape)
            .background(InkRaised)
            .border(1.dp, InkHairline, shape)
            .clickableNoRipple { if (!state.loading) onClick() }
            .padding(12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "Show 30 more",
                style = MaterialTheme.typography.titleSmall,
                color = Chalk,
                textAlign = TextAlign.Center,
            )
            if (state.loading) {
                CircularProgressIndicator(color = Accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
            } else {
                Text(
                    text = state.rangeLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = ChalkMuted,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
