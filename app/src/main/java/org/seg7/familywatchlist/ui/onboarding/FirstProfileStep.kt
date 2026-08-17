package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import org.seg7.familywatchlist.ui.avatar.AvatarBadge
import org.seg7.familywatchlist.ui.avatar.AvatarPickerGrid
import org.seg7.familywatchlist.ui.avatar.defaultAvatarOption
import org.seg7.familywatchlist.ui.avatar.toAvatarKey
import org.seg7.familywatchlist.ui.profile.AgeRatingCapSelector
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Chalk
import org.seg7.familywatchlist.ui.theme.ChalkFaint
import org.seg7.familywatchlist.ui.theme.ChalkMuted
import org.seg7.familywatchlist.ui.theme.Crimson
import org.seg7.familywatchlist.ui.theme.Dimens
import org.seg7.familywatchlist.ui.theme.Ink
import org.seg7.familywatchlist.ui.theme.InkHairline
import org.seg7.familywatchlist.ui.theme.InkRaised
import org.seg7.familywatchlist.ui.theme.OnAccent

/**
 * PLAN.md §5 screen 1's final step: create the first profile.
 *
 * The copy answers the same question §5a's third known defect raises on the profile picker —
 * one profile per person, not one shared "Family" profile — at the moment the very first
 * profile is created, which is where the misunderstanding actually starts.
 */
@Composable
fun FirstProfileStep(
    errorMessage: String?,
    onCreate: (name: String, avatarKey: String, ageRatingCap: String?) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var name by remember { mutableStateOf("") }
    var avatar by remember { mutableStateOf(defaultAvatarOption()) }
    var ageRatingCap by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Ink)
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = Dimens.Gutter, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Your profile", style = MaterialTheme.typography.displaySmall, color = Chalk)
        Text(
            text = "Make one for yourself now. Everyone in the house gets their own profile " +
                "(up to 10) so the app can learn each person's taste separately — you can add " +
                "the rest from the profile screen in a moment.",
            style = MaterialTheme.typography.bodyMedium,
            color = ChalkMuted,
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            AvatarBadge(option = avatar, size = 62.dp, name = name)
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text("Your name", color = ChalkFaint) },
                singleLine = true,
                shape = MaterialTheme.shapes.medium,
                modifier = Modifier.weight(1f),
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
        }

        Text(text = "AVATAR", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        // LazyVerticalGrid needs a bounded height inside a scrolling Column, or it measures
        // against an infinite max-height constraint and crashes (Compose's own guidance on this
        // exact pattern). 4 rows of the 6-column grid covers all 24 presets.
        AvatarPickerGrid(
            selected = avatar,
            onSelect = { avatar = it },
            name = name,
            modifier = Modifier.fillMaxWidth().height(216.dp),
        )

        Text(text = "AGE RATING CAP (OPTIONAL)", style = MaterialTheme.typography.labelSmall, color = ChalkFaint)
        AgeRatingCapSelector(selected = ageRatingCap, onSelect = { ageRatingCap = it })

        if (errorMessage != null) {
            Text(text = errorMessage, color = Crimson, style = MaterialTheme.typography.bodyMedium)
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            TextButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text("Back", color = ChalkMuted, style = MaterialTheme.typography.labelLarge)
            }
            Button(
                onClick = { onCreate(name, avatar.toAvatarKey(), ageRatingCap) },
                modifier = Modifier.weight(2f),
                shape = MaterialTheme.shapes.small,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = OnAccent),
            ) {
                Text("Finish setup", style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}
