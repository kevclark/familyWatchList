package org.seg7.familywatchlist.ui.history

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import org.seg7.familywatchlist.data.local.entity.MediaType
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.components.FilterPill
import org.seg7.familywatchlist.ui.components.PosterArt
import org.seg7.familywatchlist.ui.components.clickableNoRipple
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Crimson
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * PLAN.md §5 screen 7: reverse-chronological history, filter by profile, tap to edit or delete.
 *
 * Each row leads with the poster — even in a list screen, art carries the recognition (§5a:
 * imagery over text). Editing reuses the log-watch sheet in edit mode rather than a separate
 * form, so "who watched / when / how was it" is asked the same way everywhere in the app.
 */
@Composable
fun HistoryScreen(
    activeProfileId: Long,
    onEditEvent: (eventId: Long, tmdbId: Int, mediaType: MediaType) -> Unit,
    onOpenTitle: (Int, MediaType) -> Unit,
    modifier: Modifier = Modifier,
) {
    val container = LocalAppContainer.current
    val viewModel: HistoryViewModel = viewModel(
        factory = viewModelFactory {
            initializer { HistoryViewModel(container.watchEventRepository, container.profileRepository) }
        },
    )
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf<HistoryRow?>(null) }

    Column(modifier = modifier.fillMaxSize().background(Ink)) {
        Text(
            text = "History",
            style = MaterialTheme.typography.displaySmall,
            color = Chalk,
            modifier = Modifier.statusBarsPadding().padding(horizontal = Dimens.Gutter, vertical = 12.dp),
        )

        if (state.profiles.size > 1) {
            LazyRow(
                contentPadding = PaddingValues(horizontal = Dimens.Gutter),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp),
            ) {
                item(key = "everyone") {
                    FilterPill(
                        label = "Everyone",
                        selected = state.filterProfileId == null,
                        onClick = { viewModel.setFilter(null) },
                    )
                }
                items(state.profiles, key = { it.id }) { profile ->
                    FilterPill(
                        label = profile.name,
                        selected = state.filterProfileId == profile.id,
                        onClick = { viewModel.setFilter(profile.id) },
                        leading = {
                            AvatarBadge(
                                option = avatarKeyToOption(profile.avatarKey),
                                size = 20.dp,
                                name = profile.name,
                            )
                        },
                    )
                }
            }
        }

        when {
            state.isEmpty -> EmptyMessage(
                headline = "Nothing logged yet",
                body = "Open a title and tap “Log a watch” — history builds itself from there, " +
                    "and it's what teaches the app your family's taste.",
            )

            state.rows.isEmpty() -> EmptyMessage(
                headline = "Nothing here",
                body = "No watches logged for this person yet.",
            )

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = Dimens.Gutter,
                    end = Dimens.Gutter,
                    top = 8.dp,
                    bottom = 28.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                items(state.rows, key = { it.event.id }) { row ->
                    HistoryRowItem(
                        row = row,
                        onOpenTitle = { onOpenTitle(row.event.tmdbId, row.event.mediaType) },
                        onEdit = { onEditEvent(row.event.id, row.event.tmdbId, row.event.mediaType) },
                        onDelete = { confirmDelete = row },
                    )
                }
            }
        }
    }

    confirmDelete?.let { row ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = InkRaised,
            titleContentColor = Chalk,
            textContentColor = ChalkMuted,
            title = { Text("Delete this watch?") },
            text = {
                Text(
                    "“${row.event.title.orEmpty()}” on ${row.event.watchedAt.displayLabel()} will be " +
                        "removed from history. Any thumbs ratings stay — they're about the title, not this watch.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteEvent(row.event.id)
                    confirmDelete = null
                }) { Text("Delete", color = Crimson) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = ChalkMuted) }
            },
        )
    }
}

@Composable
private fun HistoryRowItem(
    row: HistoryRow,
    onOpenTitle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(52.dp)
                .aspectRatio(Dimens.PosterAspect)
                .clip(MaterialTheme.shapes.extraSmall)
                .clickableNoRipple(onOpenTitle),
        ) {
            PosterArt(title = row.event.title, posterPath = row.event.posterPath)
        }

        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(
                text = row.event.title.orEmpty(),
                style = MaterialTheme.typography.titleSmall,
                color = Chalk,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = row.event.watchedAt.displayLabel().uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = Accent,
            )
            if (row.watchedBy.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    row.watchedBy.take(5).forEach { profile ->
                        AvatarBadge(
                            option = avatarKeyToOption(profile.avatarKey),
                            size = 18.dp,
                            name = profile.name,
                        )
                    }
                    Text(
                        text = row.watchedBy.joinToString(", ") { it.name },
                        style = MaterialTheme.typography.bodySmall,
                        color = ChalkFaint,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            IconAction(Icons.Filled.Edit, "Edit this watch", onEdit)
            IconAction(Icons.Filled.Delete, "Delete this watch", onDelete, tint = Crimson)
        }
    }
}

@Composable
private fun IconAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    tint: androidx.compose.ui.graphics.Color = ChalkMuted,
) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .clip(MaterialTheme.shapes.small)
            .background(InkRaised)
            .clickableNoRipple(onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = label, tint = tint, modifier = Modifier.size(17.dp))
    }
}

@Composable
private fun EmptyMessage(headline: String, body: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineSmall,
                color = Chalk,
                textAlign = TextAlign.Center,
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = ChalkFaint,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private val HISTORY_DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM yyyy")

internal fun LocalDate.displayLabel(): String {
    val today = LocalDate.now()
    return when (this) {
        today -> "Today"
        today.minusDays(1) -> "Yesterday"
        else -> format(HISTORY_DATE_FORMAT)
    }
}
