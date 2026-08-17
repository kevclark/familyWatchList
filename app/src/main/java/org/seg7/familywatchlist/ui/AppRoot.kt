package org.seg7.familywatchlist.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import org.seg7.familywatchlist.ui.nav.MainScaffold
import org.seg7.familywatchlist.ui.onboarding.OnboardingScreen
import org.seg7.familywatchlist.ui.profile.ProfilePickerScreen
import org.seg7.familywatchlist.ui.theme.Accent
import org.seg7.familywatchlist.ui.theme.Ink

/**
 * Top-level screen switch, driven by [AppViewModel.uiState] rather than a nav-graph start
 * destination: with only three top-level states (Onboarding / ProfilePicker / Home) and no
 * back-stack semantics between them (completing onboarding should never leave a "back to
 * onboarding" entry, deleting the active profile should always bounce you to the picker), a
 * reactive `when` is simpler and correct by construction — see [AppViewModel]'s kdoc.
 */
@Composable
fun AppRoot(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: AppViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                AppViewModel(container.userPreferencesRepository, container.profileRepository)
            }
        },
    )

    val state by viewModel.uiState.collectAsStateWithLifecycle()

    when (val current = state) {
        AppStartState.Loading -> Box(
            modifier.fillMaxSize().background(Ink),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Accent)
        }

        is AppStartState.Onboarding -> OnboardingScreen(mode = current.mode, modifier = modifier)
        AppStartState.ProfilePicker -> ProfilePickerScreen(modifier = modifier)
        is AppStartState.Home -> MainScaffold(activeProfile = current.activeProfile, modifier = modifier)
    }
}
