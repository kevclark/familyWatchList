package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
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
import org.seg7.familywatchlist.ui.avatar.toAvatarKey
import org.seg7.familywatchlist.ui.profile.AgeRatingCapSelector

/** PLAN.md §5 screen 1's final onboarding step: create the first profile. */
@Composable
fun FirstProfileStep(
    errorMessage: String?,
    onCreate: (name: String, avatarKey: String, ageRatingCap: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(AVATAR_PRESETS.first()) }
    var ageRatingCap by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Who's this for?", style = MaterialTheme.typography.headlineSmall)
        Text(
            text = "Create your own profile first — you can add the rest of the family afterwards.",
            style = MaterialTheme.typography.bodyMedium,
        )
        OutlinedTextField(
            value = name,
            onValueChange = { name = it },
            label = { Text("Name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Text(text = "Pick an avatar", style = MaterialTheme.typography.titleSmall)
        // LazyVerticalGrid needs a bounded height when nested inside a scrolling Column, or it
        // measures with an infinite max-height constraint and crashes (Compose's own guidance
        // on this exact pattern). 3 rows' worth is enough to show the whole 12-item grid.
        AvatarPickerGrid(
            selected = avatar,
            onSelect = { avatar = it },
            modifier = Modifier.fillMaxWidth().height(220.dp),
        )
        Text(text = "Age rating cap (optional)", style = MaterialTheme.typography.titleSmall)
        AgeRatingCapSelector(selected = ageRatingCap, onSelect = { ageRatingCap = it })

        if (errorMessage != null) {
            Text(text = errorMessage, color = MaterialTheme.colorScheme.error)
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("Back") }
            Button(onClick = { onCreate(name, avatar.toAvatarKey(), ageRatingCap) }) {
                Text("Create profile & finish")
            }
        }
    }
}
