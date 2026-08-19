package org.seg7.familywatchlist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import org.seg7.familywatchlist.data.remote.TmdbApi

/**
 * PLAN.md §5a "Post-M2b decisions": the app's single accent colour, now a user preference
 * instead of a fixed build-time token. The four candidates from `ui/theme/Color.kt`'s
 * `AccentEmber`/`AccentAurora`/`AccentOrchid`/`AccentObsidian` swatches — kept here as a plain
 * enum (not `androidx.compose.ui.graphics.Color`) so the data layer doesn't depend on Compose;
 * `ui/theme/Color.kt`'s `AccentColor.toColor()` maps a value back to its swatch.
 */
enum class AccentColor { EMBER, AURORA, ORCHID, OBSIDIAN }

/**
 * PLAN.md §5 screens 1-2: the onboarding-complete flag (one-time, but reachable again from
 * Settings later) and the "active profile" selection, both backed by Jetpack DataStore
 * (PLAN.md §1). [activeProfileId] is nullable — null means "no profile currently selected",
 * which is what routes the app to the profile picker.
 */
class UserPreferencesRepository(private val dataStore: DataStore<Preferences>) {

    val onboardingComplete: Flow<Boolean> =
        dataStore.data.map { it[ONBOARDING_COMPLETE] ?: false }

    val activeProfileId: Flow<Long?> =
        dataStore.data.map { it[ACTIVE_PROFILE_ID] }

    /**
     * Set when the user opens Settings → "Services & attribution setup" (PLAN.md §5a known
     * defect: re-entered onboarding had no way back).
     *
     * This is a *separate* flag rather than reusing [onboardingComplete], which is what M2a
     * did — flipping onboardingComplete back to false made re-configuration indistinguishable
     * from a first run, so the flow replayed attribution and profile creation and offered no
     * exit. Keeping the two apart means the app can tell "this user has never set up" from
     * "this user wants to change their services", show the right entry step, and offer a close
     * button that just clears this flag and drops them back where they were.
     */
    val servicesSetupRequested: Flow<Boolean> =
        dataStore.data.map { it[SERVICES_SETUP_REQUESTED] ?: false }

    /**
     * PLAN.md §5a "Post-M2b decisions": accent colour as a live preference, default
     * [AccentColor.OBSIDIAN]. Stored as the enum's name; an unrecognised or missing value falls
     * back to the default rather than crashing, same defensive posture as the rest of this file.
     */
    val accentColor: Flow<AccentColor> =
        dataStore.data.map { prefs ->
            prefs[ACCENT_COLOR]?.let { raw -> runCatching { AccentColor.valueOf(raw) }.getOrNull() }
                ?: AccentColor.OBSIDIAN
        }

    /**
     * PLAN.md §7 M2f: TMDB doesn't do IP geolocation — `watch_region` is an explicit parameter
     * this app sends, so it has to live somewhere the user can change it. An ISO 3166-1 alpha-2
     * code (e.g. "GB", "US"), stored as a plain string rather than an enum since the valid set
     * comes from TMDB's own `/watch/providers/regions` endpoint ([RegionCatalogRepository]), not
     * a fixed list this app hand-maintains. A missing or malformed stored value (anything that
     * isn't exactly 2 characters) falls back to [DEFAULT_REGION], same defensive posture as
     * [accentColor].
     */
    val region: Flow<String> =
        dataStore.data.map { prefs -> prefs[REGION]?.takeIf { it.length == 2 } ?: DEFAULT_REGION }

    /**
     * PLAN.md §7 M2f's open design question, resolved: subscribed-provider IDs are
     * region-specific (BBC iPlayer/Channel 4/ITVX don't exist outside the UK), so switching
     * region can leave the existing subscribed list producing sparse/empty results without
     * erroring. Rather than auto-clearing the subscribed list (surprising and lossy) or silently
     * accepting degraded results, this flag drives a dismissible inline notice in Settings
     * prompting the user back into the services picker — visible until they've revisited it
     * (cleared by [org.seg7.familywatchlist.ui.onboarding.OnboardingViewModel]'s services-step
     * exit paths, `dismiss()`/`onServicesConfirmed()`), not auto-cleared by anything else.
     */
    val regionServicesMismatch: Flow<Boolean> =
        dataStore.data.map { it[REGION_SERVICES_MISMATCH] ?: false }

    suspend fun setOnboardingComplete(complete: Boolean) {
        dataStore.edit { it[ONBOARDING_COMPLETE] = complete }
    }

    suspend fun setServicesSetupRequested(requested: Boolean) {
        dataStore.edit { it[SERVICES_SETUP_REQUESTED] = requested }
    }

    suspend fun setActiveProfileId(id: Long) {
        dataStore.edit { it[ACTIVE_PROFILE_ID] = id }
    }

    suspend fun clearActiveProfileId() {
        dataStore.edit { it.remove(ACTIVE_PROFILE_ID) }
    }

    suspend fun setAccentColor(accent: AccentColor) {
        dataStore.edit { it[ACCENT_COLOR] = accent.name }
    }

    /**
     * Persists the new region and, when it's a genuine change from whatever was in effect
     * before (explicit or [DEFAULT_REGION]), flags [regionServicesMismatch] — the subscribed
     * provider list was chosen for the *old* region and may not apply to the new one.
     */
    suspend fun setRegion(region: String) {
        dataStore.edit { prefs ->
            val previous = prefs[REGION] ?: DEFAULT_REGION
            prefs[REGION] = region
            if (previous != region) prefs[REGION_SERVICES_MISMATCH] = true
        }
    }

    /** The user has revisited (or dismissed their way past) the services picker after a region change. */
    suspend fun clearRegionServicesMismatch() {
        dataStore.edit { it[REGION_SERVICES_MISMATCH] = false }
    }

    companion object {
        val ONBOARDING_COMPLETE: Preferences.Key<Boolean> = booleanPreferencesKey("onboarding_complete")
        val ACTIVE_PROFILE_ID: Preferences.Key<Long> = longPreferencesKey("active_profile_id")
        val SERVICES_SETUP_REQUESTED: Preferences.Key<Boolean> =
            booleanPreferencesKey("services_setup_requested")
        val ACCENT_COLOR: Preferences.Key<String> = stringPreferencesKey("accent_color")
        val REGION: Preferences.Key<String> = stringPreferencesKey("region")
        val REGION_SERVICES_MISMATCH: Preferences.Key<Boolean> = booleanPreferencesKey("region_services_mismatch")
        const val DEFAULT_REGION: String = TmdbApi.REGION_GB
    }
}
