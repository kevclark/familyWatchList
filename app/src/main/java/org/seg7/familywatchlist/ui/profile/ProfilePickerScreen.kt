package org.seg7.familywatchlist.ui.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption

/**
 * PLAN.md §5 screen 2: avatar grid, add/edit/delete (hard cap of 10, enforced by
 * [org.seg7.familywatchlist.data.repository.ProfileRepository]), sets the "active profile".
 * Tapping a tile selects it; the app then moves on by itself (see [OnboardingScreen]'s kdoc —
 * same reactive-state pattern applies here via [org.seg7.familywatchlist.ui.AppViewModel]).
 */
@OptIn(ExperimentalMaterial3Api::class)
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

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = { TopAppBar(title = { Text("Who's watching?") }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(modifier = Modifier.padding(innerPadding).padding(16.dp)) {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(profiles, key = { it.id }) { profile ->
                    ProfileTile(
                        profile = profile,
                        onClick = { viewModel.selectActive(profile.id) },
                        onEdit = { editing = profile },
                        onDelete = { confirmDelete = profile },
                    )
                }
                if (!isAtCap) {
                    item(key = "add") {
                        AddProfileTile(onClick = { showAddDialog = true })
                    }
                }
            }
            if (isAtCap) {
                Text(
                    text = "You've reached the 10-profile limit — delete one to add another.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
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
            title = { Text("Delete ${profile.name}?") },
            text = { Text("Their watch history and ratings stay, but they'll no longer show up here.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProfile(profile)
                    confirmDelete = null
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = null }) { Text("Cancel") }
            },
        )
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
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
            .padding(8.dp)
            .combinedClickable(onClick = onClick, onLongClick = { showActions = true }),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        AvatarBadge(option = avatarKeyToOption(profile.avatarKey), size = 72.dp)
        Text(
            text = profile.name,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
        )
        if (profile.ageRatingCap != null) {
            Text(
                text = "Cap: ${profile.ageRatingCap}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    if (showActions) {
        AlertDialog(
            onDismissRequest = { showActions = false },
            title = { Text(profile.name) },
            text = { Text("What would you like to do?") },
            confirmButton = {
                TextButton(onClick = {
                    showActions = false
                    onEdit()
                }) { Text("Edit") }
            },
            dismissButton = {
                TextButton(onClick = {
                    showActions = false
                    onDelete()
                }) { Text("Delete") }
            },
        )
    }
}

@Composable
private fun AddProfileTile(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(imageVector = Icons.Filled.Add, contentDescription = "Add profile")
        }
        Text(text = "Add profile", style = MaterialTheme.typography.bodyMedium)
    }
}
