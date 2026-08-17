package org.seg7.familywatchlist.ui.profile

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
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
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Crimson
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised

/**
 * PLAN.md §5 screen 2: avatar grid, add/edit/delete (hard cap of 10, enforced by
 * [org.seg7.familywatchlist.data.repository.ProfileRepository]), sets the "active profile".
 *
 * **Fixes PLAN.md §5a's third known defect** — "'Who's watching?' doesn't explain the model."
 * The subtitle now states it outright: one profile per person, up to 10, and picking several
 * people at once happens when you log a watch, not by creating a shared "Family" profile. That
 * was the exact ambiguity Kev hit ("Not sure if I have to create every user, or just me or a
 * 'Family' user").
 *
 * Visually §5a: rounded-square tiles on near-black, no app bar, and the active profile ringed
 * in the accent.
 */
@Composable
fun ProfilePickerScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: ProfileViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                ProfileViewModel(container.profileRepository, container.userPreferencesRepository)
            }
        },
    )

    val profiles by viewModel.profiles.collectAsState()
    val isAtCap by viewModel.isAtProfileCap.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }

    var showAddDialog by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<ProfileEntity?>(null) }
    var confirmDelete by remember { mutableStateOf<ProfileEntity?>(null) }

    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            when (event) {
                is ProfileUiEvent.Error -> snackbarHostState.showSnackbar(event.message)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Ink)) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = Dimens.Gutter,
                end = Dimens.Gutter,
                bottom = 32.dp,
            ),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column(
                    modifier = Modifier.statusBarsPadding().padding(top = 24.dp, bottom = 20.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Text(
                        text = "Who's watching?",
                        style = MaterialTheme.typography.displayMedium,
                        color = Chalk,
                    )
                    // The clarifying copy — §5a known defect #3.
                    Text(
                        text = "One profile per person, up to 10 — that's how the app learns each " +
                            "of your tastes separately. There's no shared \"Family\" profile to " +
                            "create: when several of you watch something together, you tick " +
                            "everyone at once while logging it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = ChalkMuted,
                    )
                }
            }

            items(profiles, key = { it.id }) { profile ->
                ProfileTile(
                    profile = profile,
                    onClick = { viewModel.selectActive(profile.id) },
                    onEdit = { editing = profile },
                    onDelete = { confirmDelete = profile },
                )
            }
            if (!isAtCap) {
                item(key = "add") { AddProfileTile(onClick = { showAddDialog = true }) }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "footer") {
                Text(
                    text = if (isAtCap) {
                        "You've reached the 10-profile limit — delete one to add another."
                    } else {
                        "Long-press a profile to edit or delete it."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkFaint,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        }

        SnackbarHost(snackbarHostState, modifier = Modifier.align(Alignment.BottomCenter))
    }

    if (showAddDialog) {
        ProfileEditDialog(
            initial = null,
            onDismiss = { showAddDialog = false },
            onSave = { name, avatarKey, ageCap ->
                viewModel.addProfile(name, avatarKey, ageCap)
                showAddDialog = false
            },
        )
    }

    editing?.let { profile ->
        ProfileEditDialog(
            initial = ProfileEditInitial(profile.name, profile.avatarKey, profile.ageRatingCap),
            onDismiss = { editing = null },
            onSave = { name, avatarKey, ageCap ->
                viewModel.updateProfile(profile, name, avatarKey, ageCap)
                editing = null
            },
        )
    }

    confirmDelete?.let { profile ->
        AlertDialog(
            onDismissRequest = { confirmDelete = null },
            containerColor = InkRaised,
            titleContentColor = Chalk,
            textContentColor = ChalkMuted,
            title = { Text("Delete ${profile.name}?") },
            text = { Text("Their watch history and ratings stay, but they'll no longer show up here.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile)
                    confirmDelete = null
                }) { Text("Delete", color = Crimson) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel", color = ChalkMuted) }
            },
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ProfileTile(
    profile: ProfileEntity,
    onClick: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showActions by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(vertical = 10.dp)
            .combinedClickable(onClick = onClick, onLongClick = { showActions = true }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AvatarBadge(option = avatarKeyToOption(profile.avatarKey), size = 82.dp, name = profile.name)
        Text(
            text = profile.name,
            style = MaterialTheme.typography.titleSmall,
            color = Chalk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        if (profile.ageRatingCap != null) {
            Text(
                text = "UP TO ${profile.ageRatingCap}",
                style = MaterialTheme.typography.labelSmall,
                color = ChalkFaint,
            )
        }
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            containerColor = InkRaised,
            titleContentColor = Chalk,
            textContentColor = ChalkMuted,
            title = { Text(profile.name) },
            text = { Text("What would you like to do?") },
            confirmButton = {
                TextButton(onClick = {
                    showActions = false
                    onEdit()
                }) { Text("Edit", color = Accent) }
            },
            dismissButton = {
                TextButton(onClick = {
                    showActions = false
                    onDelete()
                }) { Text("Delete", color = Crimson) }
            },
        )
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .padding(vertical = 10.dp)
            .clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(
            modifier = Modifier
                .size(82.dp)
                .clip(MaterialTheme.shapes.large)
                .background(InkRaised)
                .border(1.dp, InkHairline, MaterialTheme.shapes.large),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Add profile",
                tint = ChalkMuted,
                modifier = Modifier.size(30.dp),
            )
        }
        Text(text = "Add someone", style = MaterialTheme.typography.titleSmall, color = ChalkMuted)
    }
}
