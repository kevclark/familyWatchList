package org.seg7.familywatchlist.ui.profile

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.AvatarPickerGrid
import org.seg7.familywatchlist.ui.avatar.avatarKeyToOption
import org.seg7.familywatchlist.ui.avatar.defaultAvatarOption
import org.seg7.familywatchlist.ui.avatar.toAvatarKey
import org.seg7.familywatchlist.ui.components.FilterPill
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised

/** Shared create/edit form for the Family profile (PLAN.md §4, M3d). `initial == null` means "create"; otherwise "edit". */
data class FamilyProfileEditInitial(
    val name: String,
    val avatarKey: String,
    val memberProfileIds: Set<Long>,
)

/**
 * PLAN.md §4: "+ Create family profile" reachable from the profile picker alongside adding an
 * individual — pick 2+ existing profiles as members, name it (default "Family"), pick an avatar
 * (reusing [AvatarPickerGrid], same as [ProfileEditDialog]). Membership stays editable afterward
 * (same dialog, [initial] non-null) — not a create-only flow.
 *
 * [existingProfiles] is every real profile on the account — the pool this dialog picks members
 * from. The picker screen only ever offers this dialog once there are 2+ of them (see
 * `ProfilePickerScreen`'s visibility gating), but the *save* floor
 * ([FamilyProfileRepository.MIN_MEMBERS]) is re-enforced here too via [confirmEnabled], not just
 * assumed from that gate — a member could still be untoggled back down to fewer than 2 inside
 * this dialog.
 */
@Composable
fun FamilyProfileEditDialog(
    existingProfiles: List<ProfileEntity>,
    initial: FamilyProfileEditInitial?,
    onDismiss: () -> Unit,
    onSave: (name: String, avatarKey: String, memberProfileIds: List<Long>) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var avatar by remember {
        mutableStateOf(initial?.avatarKey?.let(::avatarKeyToOption) ?: defaultAvatarOption())
    }
    var selectedMemberIds by remember { mutableStateOf(initial?.memberProfileIds ?: emptySet()) }
    val confirmEnabled = selectedMemberIds.size >= FamilyProfileRepository.MIN_MEMBERS

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = InkRaised,
        titleContentColor = Chalk,
        textContentColor = ChalkMuted,
        title = { Text(if (initial == null) "Create family profile" else "Edit family profile") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "A shared profile for the people who watch together most — its own Home, " +
                        "picks blended from everyone picked below. Deleting a member here just " +
                        "removes them from the family; it never deletes their own profile.",
                    style = MaterialTheme.typography.bodySmall,
                    color = ChalkFaint,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name", color = ChalkFaint) },
                    placeholder = { Text(FamilyProfileRepository.DEFAULT_NAME, color = ChalkFaint) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Accent,
                        unfocusedBorderColor = InkHairline,
                        cursorColor = Accent,
                        focusedTextColor = Chalk,
                        unfocusedTextColor = Chalk,
                    ),
                )
                Text("Avatar", color = ChalkMuted)
                AvatarPickerGrid(
                    selected = avatar,
                    onSelect = { avatar = it },
                    name = name.ifBlank { FamilyProfileRepository.DEFAULT_NAME },
                    modifier = Modifier.fillMaxWidth().height(216.dp),
                )
                Text(
                    text = "Members (pick ${FamilyProfileRepository.MIN_MEMBERS}+)",
                    color = ChalkMuted,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    existingProfiles.forEach { profile ->
                        val selected = profile.id in selectedMemberIds
                        FilterPill(
                            label = profile.name,
                            selected = selected,
                            onClick = {
                                selectedMemberIds = if (selected) {
                                    selectedMemberIds - profile.id
                                } else {
                                    selectedMemberIds + profile.id
                                }
                            },
                            leading = {
                                AvatarBadge(option = avatarKeyToOption(profile.avatarKey), size = 20.dp, name = profile.name)
                            },
                        )
                    }
                }
                if (!confirmEnabled) {
                    Text(
                        text = "A family of one person isn't a blend — pick at least ${FamilyProfileRepository.MIN_MEMBERS}.",
                        style = MaterialTheme.typography.bodySmall,
                        color = ChalkFaint,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name, avatar.toAvatarKey(), selectedMemberIds.toList()) },
                enabled = confirmEnabled,
            ) {
                Text("Save", color = if (confirmEnabled) Accent else ChalkFaint)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = ChalkMuted) }
        },
    )
}
