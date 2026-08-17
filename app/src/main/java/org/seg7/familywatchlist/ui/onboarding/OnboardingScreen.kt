package org.seg7.familywatchlist.ui.onboarding

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import org.seg7.familywatchlist.ui.LocalAppContainer
import org.seg7.familywatchlist.ui.theme.Ink

/**
 * PLAN.md §5 screen 1: attribution -> subscribed services -> first profile.
 *
 * [mode] decides whether that's the whole flow or just the services step (PLAN.md §5a's second
 * known defect — re-entering from Settings used to replay everything with no way out). Step
 * transitions stay internal rather than going through the nav graph: three steps in a fixed
 * order don't need back-stack entries of their own, and the calling
 * [org.seg7.familywatchlist.ui.AppRoot] swaps this whole screen out reactively when either
 * DataStore flag changes.
 *
 * No `Scaffold` — §5a wants no Material chrome here, and each step paints its own full-bleed
 * surface with its own insets so it can run edge-to-edge.
 */
@Composable
fun OnboardingScreen(mode: OnboardingMode, modifier: Modifier = Modifier) {
    val container = LocalAppContainer.current
    val viewModel: OnboardingViewModel = viewModel(
        key = "onboarding-$mode",
        factory = viewModelFactory {
            initializer {
                OnboardingViewModel(
                    container.providerRepository,
                    container.profileRepository,
                    container.userPreferencesRepository,
                    mode,
                )
            }
        },
    )

    val step by viewModel.step.collectAsState()
    val providers by viewModel.providers.collectAsState()
    val serviceQuery by viewModel.serviceQuery.collectAsState()
    val subscribedCount by viewModel.subscribedCount.collectAsState()
    val servicesLoadState by viewModel.servicesLoadState.collectAsState()
    val completionState by viewModel.completionState.collectAsState()

    AnimatedContent(
        targetState = step,
        modifier = modifier.fillMaxSize().background(Ink),
        transitionSpec = { fadeIn() togetherWith fadeOut() },
        label = "onboarding-step",
    ) { currentStep ->
        when (currentStep) {
            OnboardingStep.ATTRIBUTION -> AttributionStep(
                onAcknowledge = viewModel::onAttributionAcknowledged,
            )

            OnboardingStep.SERVICES -> ServicesPickerStep(
                providers = providers,
                query = serviceQuery,
                subscribedCount = subscribedCount,
                loadState = servicesLoadState,
                canDismiss = viewModel.canDismiss,
                onQueryChange = viewModel::onServiceQueryChange,
                onToggle = viewModel::toggleProvider,
                onConfirm = viewModel::onServicesConfirmed,
                onRetry = viewModel::retryLoadServices,
                onSkip = viewModel::skipServicesLoad,
                onBack = viewModel::onBack,
                onDismiss = viewModel::dismiss,
            )

            OnboardingStep.PROFILE -> FirstProfileStep(
                errorMessage = (completionState as? OnboardingCompletionState.Error)?.message,
                onCreate = viewModel::completeOnboarding,
                onBack = viewModel::onBack,
            )
        }
    }
}
