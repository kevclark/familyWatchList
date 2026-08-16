package org.seg7.familywatchlist.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.ui.avatar.AVATAR_PRESETS
import org.seg7.familywatchlist.ui.avatar.AvatarPickerGrid
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.avatar.toAvatarKey

/** Shared add/edit form (PLAN.md §5 screen 2). `initial == null` means "add"; otherwise "edit". */
data class ProfileEditInitial(
    val name: String,
    val avatarKey: String,
    val ageRatingCap: String?,
)

@Composable
fun ProfileEditDialog(
    initial: ProfileEditInitial?,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarKey: String, ageRatingCap: String?) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var avatar by remember {
        mutableStateOf(initial?.avatarKey?.let(::avatarKeyToOption) ?: AVATAR_PRESETS.first())
    }
    var ageRatingCap by remember { mutableStateOf(initial?.ageRatingCap) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) "Add profile" else "Edit profile") },
        text = {
            // The Column scrolls (dialogs get a bounded max height from AlertDialog itself,
            // and content can exceed it on smaller screens); the grid gets an explicit height
            // so all 3 rows show without clipping instead of racing the scroll for space —
            // same infinite-constraint pitfall as FirstProfileStep, avoided the same way.
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("Avatar")
                AvatarPickerGrid(
                    selected = avatar,
                    onSelect = { avatar = it },
                    modifier = Modifier.fillMaxWidth().height(230.dp),
                )
                Text("Age rating cap")
                AgeRatingCapSelector(selected = ageRatingCap, onSelect = { ageRatingCap = it })
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name, avatar.toAvatarKey(), ageRatingCap) }) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}
