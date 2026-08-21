package org.seg7.familywatchlist.ui

import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity

/**
 * PLAN.md §4 "The Family profile" (M3d): the resolved "who is active" identity that
 * [AppStartState.Home] carries and [org.seg7.familywatchlist.ui.nav.MainScaffold] threads down to
 * every screen — a real [ProfileEntity] (unchanged from M2/M3) or the singleton Family profile.
 *
 * [id] is [FAMILY_PROFILE_SENTINEL_ID] for [Family] — see that constant's kdoc for why a sentinel
 * `Long`, not a richer wrapper type, is what actually flows through the app: every *existing*
 * `activeProfileId: Long` parameter across the app (Search/MyList/TitleDetail/LogWatch/History/
 * TunePicks — all predating this feature) keeps compiling and working unchanged, because a
 * negative id can never collide with a real one. Only the handful of sites that must actually
 * branch on "is this Family" — enumerated in PROGRESS.md's M3d entry — add an explicit
 * `== FAMILY_PROFILE_SENTINEL_ID` (or [isFamily]) check; everywhere else, [id] alone is enough
 * to keep behaving exactly as it always did (per-scope shortlist keys, "who added this list
 * item" filters, etc. all naturally read as "whatever was active" without needing to know which
 * kind of active profile that was).
 */
sealed interface ActiveProfile {
    val id: Long
    val name: String
    val avatarKey: String
    val isFamily: Boolean

    data class Individual(val profile: ProfileEntity) : ActiveProfile {
        override val id: Long get() = profile.id
        override val name: String get() = profile.name
        override val avatarKey: String get() = profile.avatarKey
        override val isFamily: Boolean get() = false
    }

    /**
     * [memberProfileIds] is the curated membership at the moment this was resolved — used by
     * Home (persisted family shortlist) and the log-watch sheet (auto-tag-all-members) so neither
     * has to re-query [org.seg7.familywatchlist.data.repository.FamilyProfileRepository] itself.
     * [AppViewModel.resolveStartState] only ever constructs this variant when membership is
     * [org.seg7.familywatchlist.data.repository.FamilyProfileWithMembers.hasEnoughMembers] —
     * PLAN.md §4's under-2-members edge case bounces back to the profile picker instead (see that
     * function's kdoc), so [memberProfileIds] here always has 2+ entries in practice.
     */
    data class Family(val familyProfile: FamilyProfileEntity, val memberProfileIds: List<Long>) : ActiveProfile {
        override val id: Long get() = FAMILY_PROFILE_SENTINEL_ID
        override val name: String get() = familyProfile.name
        override val avatarKey: String get() = familyProfile.avatarKey
        override val isFamily: Boolean get() = true
    }
}
