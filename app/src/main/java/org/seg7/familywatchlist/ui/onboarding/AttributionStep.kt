package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.Image
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.R
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.OnAccent

/**
 * PLAN.md §3 Attribution: the exact required TMDB notice, verbatim from `strings.xml`, plus the
 * JustWatch credit. First screen of onboarding — nothing else shows until it's acknowledged.
 *
 * Reworked to §5a: no boxed `Card`s (M2a stacked two of them and it read as a consent form),
 * a big condensed wordmark, and the legal text set quietly at the bottom where legal text
 * belongs. The requirement is that the notices are *shown*, not that they dominate.
 */
@Composable
fun AttributionStep(
    onAcknowledge: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = Dimens.Gutter, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            text = "FAMILY",
            style = MaterialTheme.typography.displayLarge,
            color = Chalk,
        )
        Text(
            text = "WATCHLIST",
            style = MaterialTheme.typography.displayLarge,
            color = Accent,
            modifier = Modifier.padding(top = 0.dp),
        )
        Text(
            text = "Everything you've watched, everything you want to — and what's actually " +
                "streaming on the services you already pay for.",
            style = MaterialTheme.typography.bodyLarge,
            color = ChalkMuted,
            modifier = Modifier.padding(top = 6.dp),
        )

        // Brand block sits at the top, action and small print at the bottom — a fixed-height
        // Column with a weighted spacer rather than a scrolling one, because this screen's copy
        // is short and fixed, and a top-stacked layout left a screenful of dead space below it.
        Spacer(modifier = Modifier.weight(1f))

        Button(
            onClick = onAcknowledge,
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.small,
            colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
        ) {
            Text("Get started", style = MaterialTheme.typography.labelLarge)
        }

        HorizontalDivider(color = InkHairline, modifier = Modifier.padding(top = 14.dp))

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = "DATA & ATTRIBUTION",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
            )
            // PLAN.md §3: "TMDB logo + the exact notice ... in Settings → About and on
            // onboarding" — the logo half of that requirement, alongside the notice text below.
            Image(
                painter = painterResource(R.drawable.ic_tmdb_logo),
                contentDescription = "The Movie Database (TMDB)",
                modifier = Modifier.padding(bottom = 2.dp),
            )
            Text(
                text = stringResource(R.string.tmdb_attribution),
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint,
            )
            Text(
                text = stringResource(R.string.justwatch_attribution) +
                    ". Availability is best-effort — always double-check on the service itself.",
                style = MaterialTheme.typography.bodySmall,
                color = ChalkFaint,
            )
        }
    }
}
