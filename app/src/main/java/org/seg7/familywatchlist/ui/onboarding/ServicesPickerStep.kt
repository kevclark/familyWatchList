package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.components.providerLogoUrl
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised
import org.seg7.familywatchlist.ui.theme.OnAccent

/**
 * PLAN.md §5 screen 1 + §2: the subscribed-services picker, seeded from the Provider table with
 * the GB defaults pre-ticked.
 *
 * **Fixes PLAN.md §5a's first known defect** — "Services picker has no filter. With the full GB
 * provider list it's a long unsearchable list." There's now a search field doing a
 * case-insensitive substring match on the provider name (the plan explicitly says substring is
 * enough; true fuzzy matching would be overkill for ~100 rows), plus a running count of what's
 * ticked so the total stays visible while filtering.
 *
 * Rows are logo-led rather than a name-and-Switch form — a service is recognised by its mark
 * long before its name is read, and the M2a `Switch` list is exactly the "form, not a streamer"
 * quality Kev flagged.
 */
@Composable
fun ServicesPickerStep(
    providers: List<ProviderEntity>,
    query: String,
    subscribedCount: Int,
    loadState: ServicesLoadState,
    canDismiss: Boolean,
    onQueryChange: (String) -> Unit,
    onToggle: (providerId: Int, subscribed: Boolean) -> Unit,
    onConfirm: () -> Unit,
    onRetry: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxSize().background(Ink)) {
        Column(
            modifier = Modifier.statusBarsPadding().padding(horizontal = Dimens.Gutter),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Text(
                    text = "Your services",
                    style = MaterialTheme.typography.displaySmall,
                    color = Chalk,
                    modifier = Modifier.weight(1f),
                )
                // The way out of a re-entered flow (PLAN.md §5a known defect #2). Only rendered
                // in RECONFIGURE mode — during a genuine first run there is nowhere to go back to.
                if (canDismiss) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "Close services setup",
                        tint = ChalkMuted,
                        modifier = Modifier
                            .size(26.dp)
                            .clickableNoRipple(onDismiss),
                    )
                }
            }
            Text(
                text = "Pick what your household subscribes to. We'll only recommend things you can " +
                    "actually watch tonight — change these any time in Settings.",
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkMuted,
            )

            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Find a service", color = ChalkFaint) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = ChalkMuted) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        Icon(
                            Icons.Filled.Clear,
                            contentDescription = "Clear filter",
                            tint = ChalkMuted,
                            modifier = Modifier.clickableNoRipple { onQueryChange("") },
                        )
                    }
                },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = InkRaised,
                    unfocusedContainerColor = InkRaised,
                    focusedBorderColor = Accent,
                    unfocusedBorderColor = InkHairline,
                    cursorColor = Accent,
                    focusedTextColor = Chalk,
                    unfocusedTextColor = Chalk,
                ),
            )
            Text(
                text = when (subscribedCount) {
                    0 -> "Nothing selected yet"
                    1 -> "1 service selected"
                    else -> "$subscribedCount services selected"
                },
                style = MaterialTheme.typography.labelSmall,
                color = if (subscribedCount > 0) Accent else ChalkFaint,
            )
        }

        Box(modifier = Modifier.fillMaxSize().weight(1f)) {
            when (loadState) {
                ServicesLoadState.Loading -> Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) { CircularProgressIndicator(color = Accent) }

                is ServicesLoadState.Error -> Column(
                    modifier = Modifier.fillMaxSize().padding(Dimens.Gutter),
                    verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Couldn't load streaming services",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Chalk,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "${loadState.message}. You can set these up later in Settings.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkFaint,
                        textAlign = TextAlign.Center,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedButton(
                            onClick = onRetry,
                            shape = MaterialTheme.shapes.small,
                            border = androidx.compose.foundation.BorderStroke(1.dp, InkHairline),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Chalk),
                        ) { Text("Retry") }
                        TextButton(onClick = onSkip) { Text("Skip for now", color = ChalkMuted) }
                    }
                }

                ServicesLoadState.Loaded -> if (providers.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(Dimens.Gutter),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "No service matches “$query”.",
                            style = MaterialTheme.typography.bodyLarge,
                            color = ChalkMuted,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = Dimens.Gutter,
                            end = Dimens.Gutter,
                            top = 10.dp,
                            bottom = 12.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        items(providers, key = { it.providerId }) { provider ->
                            ServiceRow(
                                provider = provider,
                                onToggle = { onToggle(provider.providerId, !provider.subscribed) },
                            )
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = Dimens.Gutter, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (!canDismiss) {
                TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                    Text("Back", color = ChalkMuted, style = MaterialTheme.typography.labelLarge)
                }
            }
            Button(
                onClick = onConfirm,
                enabled = loadState !is ServicesLoadState.Loading,
                modifier = Modifier.weight(2f),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Accent,
                    contentColor = OnAccent,
                    disabledContainerColor = InkRaised,
                    disabledContentColor = ChalkFaint,
                ),
            ) {
                Text(
                    text = if (canDismiss) "Done" else "Continue",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun ServiceRow(provider: ProviderEntity, onToggle: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (provider.subscribed) Accent.copy(alpha = 0.12f) else InkRaised)
            .clickableNoRipple(onToggle)
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        val logo = providerLogoUrl(provider.logoPath)
        Box(
            modifier = Modifier.size(34.dp).clip(RoundedCornerShape(7.dp)).background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            if (logo != null) {
                AsyncImage(model = logo, contentDescription = null, modifier = Modifier.fillMaxSize())
            } else {
                Text(
                    text = provider.name.take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = ChalkFaint,
                )
            }
        }
        Text(
            text = provider.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (provider.subscribed) Chalk else ChalkMuted,
            modifier = Modifier.weight(1f),
        )
        // A tick, not a Switch — the question is "do you have this", which is a selection,
        // and a column of Material switches is what made M2a read as a settings form.
        Box(
            modifier = Modifier
                .size(24.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (provider.subscribed) Accent else Ink),
            contentAlignment = Alignment.Center,
        ) {
            if (provider.subscribed) {
                Icon(
                    Icons.Filled.Check,
                    contentDescription = "${provider.name} selected",
                    tint = OnAccent,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }
}
