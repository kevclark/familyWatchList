package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.lifecycle.viewmodel.initializer
import org.seg7.familywatchlist.ui.LocalAppContainer

/**
 * PLAN.md §5 screen 1: attribution -> subscribed services -> first profile, one-time (gated by
 * [org.seg7.familywatchlist.data.repository.UserPreferencesRepository.onboardingComplete]).
 * Step transitions are handled internally rather than via the nav graph — three steps that
 * always run in this fixed order don't need back-stack entries of their own; the calling
 * [org.seg7.familywatchlist.ui.AppRoot] just swaps this whole screen out once
 * [OnboardingCompletionState.Done] flips [org.seg7.familywatchlist.ui.AppViewModel]'s state.
 */
@Composable
fun OnboardingScreen(modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: OnboardingViewModel = viewModel(
        factory = viewModelFactory {
            initializer {
                OnboardingViewModel(
                    container.providerRepository,
                    container.profileRepository,
                    container.userPreferencesRepository,
                )
            }
        },
    )

    val step by viewModel.step.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val servicesLoadState by viewModel.servicesLoadState.collectAsState()
    val completionState by viewModel.completionState.collectAsState()

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        AnimatedContent(
            targetState = step,
            modifier = Modifier.padding(innerPadding),
            label = "onboarding-step",
        ) { currentStep ->
            when (currentStep) {
                OnboardingStep.ATTRIBUTION -> AttributionStep(
                    onAcknowledge = viewModel::onAttributionAcknowledged,
                )

                OnboardingStep.SERVICES -> ServicesPickerStep(
                    providers = providers,
                    loadState = servicesLoadState,
                    onToggle = viewModel::toggleProvider,
                    onConfirm = viewModel::onServicesConfirmed,
                    onRetry = viewModel::retryLoadServices,
                    onSkip = viewModel::skipServicesLoad,
                    onBack = viewModel::onBack,
                )

                OnboardingStep.PROFILE -> FirstProfileStep(
                    errorMessage = (completionState as? OnboardingCompletionState.Error)?.message,
                    onCreate = viewModel::completeOnboarding,
                    onBack = viewModel::onBack,
                )
            }
        }
    }
}
