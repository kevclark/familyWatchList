package org.seg7.familywatchlist.data.repository

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.doublePreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import java.time.DayOfWeek
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

    /**
     * PLAN.md §4: "`POST_NOTIFICATIONS` runtime permission is requested during onboarding
     * (Android 13+); declining just means silent refresh." This flag makes the request one-shot
     * — set right after [org.seg7.familywatchlist.ui.AppRoot] fires the system permission
     * dialog, regardless of the user's answer, so returning to the app later never re-prompts
     * (Android itself already suppresses the dialog once granted; this is what suppresses it
     * after a *decline* too, matching "declining just means silent refresh" rather than a
     * repeated nag).
     */
    val notificationPermissionRequested: Flow<Boolean> =
        dataStore.data.map { it[NOTIFICATION_PERMISSION_REQUESTED] ?: false }

    suspend fun setNotificationPermissionRequested() {
        dataStore.edit { it[NOTIFICATION_PERMISSION_REQUESTED] = true }
    }

    /**
     * PLAN.md §4 "Per-profile notification control" (M3e): the app-level master on/off switch,
     * layered on top of (never replacing) the existing `POST_NOTIFICATIONS` OS permission check
     * in [org.seg7.familywatchlist.work.ShortlistNotifier] — both must allow it for a
     * notification to actually fire, and so must the per-profile toggle in
     * [NotificationPreferencesRepository] (see [org.seg7.familywatchlist.work.NotificationGate]
     * for where the three combine). Default **on**: before this pass there was no in-app off
     * switch at all, so defaulting off would silently change existing behaviour for everyone,
     * not just new opt-outs — PLAN.md §4's explicit "preserve current behaviour" ask.
     */
    val notificationsEnabled: Flow<Boolean> =
        dataStore.data.map { it[NOTIFICATIONS_ENABLED] ?: true }

    suspend fun setNotificationsEnabled(enabled: Boolean) {
        dataStore.edit { it[NOTIFICATIONS_ENABLED] = enabled }
    }

    /**
     * PLAN.md §4 "Configurable schedule" (M3f): the weekly refresh's day-of-week, default
     * [DEFAULT_REFRESH_DAY_OF_WEEK] (Friday). Was a hardcoded `DayOfWeek.MONDAY` literal in
     * [org.seg7.familywatchlist.work.RecommendationScheduler] through M3e — this is the first
     * time it's a real preference. Stored as the enum's name, same defensive fallback-on-garbage
     * posture as [accentColor]: an unrecognised or missing value falls back to the default rather
     * than crashing.
     *
     * Writing this alone does **not** move the underlying WorkManager job — see
     * [org.seg7.familywatchlist.work.RecommendationScheduler.rescheduleForSettingsChange]'s kdoc
     * for why a distinct reschedule call is required from wherever this is written, and
     * [setRefreshSchedule]'s kdoc for why that call doesn't live here.
     */
    val refreshDayOfWeek: Flow<DayOfWeek> =
        dataStore.data.map { prefs ->
            prefs[REFRESH_DAY_OF_WEEK]?.let { raw -> runCatching { DayOfWeek.valueOf(raw) }.getOrNull() }
                ?: DEFAULT_REFRESH_DAY_OF_WEEK
        }

    /**
     * PLAN.md §4 "Configurable schedule" (M3f): the weekly refresh's hour-of-day (0-23, always
     * `:00` — no minute granularity, matching the existing convention), default
     * [DEFAULT_REFRESH_HOUR]. An out-of-range or missing stored value falls back to the default.
     */
    val refreshHour: Flow<Int> =
        dataStore.data.map { prefs -> prefs[REFRESH_HOUR]?.takeIf { it in 0..23 } ?: DEFAULT_REFRESH_HOUR }

    /**
     * Persists the new weekly-refresh day/hour. Deliberately just a DataStore write, like every
     * other setter in this file — it has no [android.content.Context], so it can't itself call
     * [org.seg7.familywatchlist.work.RecommendationScheduler.rescheduleForSettingsChange] (which
     * needs one to reach `WorkManager.getInstance`). The caller — [ui.settings.SettingsScreen]'s
     * schedule row — is responsible for calling both this and `rescheduleForSettingsChange` when
     * the user picks a new day/hour; skipping the latter would leave the new preference stored
     * but silently inert while the old WorkManager schedule keeps running underneath it.
     */
    suspend fun setRefreshSchedule(dayOfWeek: DayOfWeek, hour: Int) {
        require(hour in 0..23) { "refresh hour must be in 0..23, was $hour" }
        dataStore.edit { prefs ->
            prefs[REFRESH_DAY_OF_WEEK] = dayOfWeek.name
            prefs[REFRESH_HOUR] = hour
        }
    }

    /**
     * PLAN.md §4a slider 4 ("Everyone's happy" <-> "Average taste wins"): the family mean/min
     * blend, stored as a **shared, app-level** preference rather than per-profile.
     *
     * **Judgment call, flagged per PLAN.md §4a's explicit ask** ("this is the one slider whose
     * UI home isn't as settled as the other three ... flag your preferred mechanism"): this
     * slider tunes a property of a *family-scope shortlist request* (which profiles are selected
     * varies per Home visit via the who's-watching chips), not any single profile's own taste —
     * so it has no natural per-profile row to live on. Two mechanisms were considered:
     *  1. **A shared app-level setting (chosen).** One value for the whole household, changed in
     *     Settings, applied to every family-scope computation until changed again. Simple, and
     *     matches how the other cross-cutting preferences here (region, accent) already work.
     *  2. Per-session, picked alongside the who's-watching chips each time a subset is chosen.
     *     More "correct" in spirit (family movie night's taste-averaging preference could
     *     plausibly differ from a different night's), but adds a control to Home's chip row that
     *     has to be re-set constantly for a single household's fairly stable preference, and has
     *     nowhere obvious to persist between sessions without inventing new state.
     * Went with (1): stored once in Settings, applied to both the weekly persisted "FAMILY"
     * shortlist and any on-the-fly who's-watching-chip recompute. If Kev finds himself wanting a
     * different blend for a specific movie night, that's the concrete signal (2) should be built
     * instead — flagged in the build report as the judgment call it is.
     */
    val familyBlendSlider: Flow<Double> =
        dataStore.data.map { prefs -> prefs[FAMILY_BLEND_SLIDER]?.takeIf { it in -1.0..1.0 } ?: 0.0 }

    suspend fun setFamilyBlendSlider(value: Double) {
        require(value in -1.0..1.0) { "family blend slider must be in [-1, 1], was $value" }
        dataStore.edit { it[FAMILY_BLEND_SLIDER] = value }
    }

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
        val FAMILY_BLEND_SLIDER: Preferences.Key<Double> = doublePreferencesKey("family_blend_slider")
        val NOTIFICATION_PERMISSION_REQUESTED: Preferences.Key<Boolean> =
            booleanPreferencesKey("notification_permission_requested")
        val NOTIFICATIONS_ENABLED: Preferences.Key<Boolean> = booleanPreferencesKey("notifications_enabled")
        val REFRESH_DAY_OF_WEEK: Preferences.Key<String> = stringPreferencesKey("refresh_day_of_week")
        val REFRESH_HOUR: Preferences.Key<Int> = intPreferencesKey("refresh_hour")
        const val DEFAULT_REGION: String = TmdbApi.REGION_GB
        val DEFAULT_REFRESH_DAY_OF_WEEK: DayOfWeek = DayOfWeek.FRIDAY
        const val DEFAULT_REFRESH_HOUR: Int = 6
    }
}
