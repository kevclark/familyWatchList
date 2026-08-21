package org.seg7.familywatchlist.ui.profile

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.seg7.familywatchlist.data.local.entity.FAMILY_PROFILE_SENTINEL_ID
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.FamilyProfileRepository
import org.seg7.familywatchlist.data.repository.FamilyProfileWithMembers
import org.seg7.familywatchlist.data.repository.MaxProfilesReachedException
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository

/**
 * Backs the profile picker (PLAN.md §5 screen 2): the avatar grid, add/edit/delete, and
 * setting the "active profile" DataStore flag. Cap enforcement (10) lives in
 * [ProfileRepository] — this ViewModel just surfaces the failure as a one-shot UI event.
 *
 * PLAN.md §4 (M3d): also backs the Family profile's create/edit/select-active flow — [familyProfile]
 * is the live singleton-or-null read, [saveFamilyProfile] is both create and edit (see
 * [FamilyProfileRepository.save]'s kdoc for why one method covers both), and [selectFamilyActive]
 * writes [FAMILY_PROFILE_SENTINEL_ID] rather than a real id, the same sentinel mechanism every
 * other `activeProfileId` read site branches on.
 */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val familyProfileRepository: FamilyProfileRepository,
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isAtProfileCap: StateFlow<Boolean> = profiles
        .map { it.size >= ProfileRepository.MAX_PROFILES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    /** Null until a Family profile has been created — see [FamilyProfileEditDialog]. */
    val familyProfile: StateFlow<FamilyProfileWithMembers?> = familyProfileRepository.observe()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _events = MutableSharedFlow<ProfileUiEvent>()
    val events = _events.asSharedFlow()

    /** Creates a profile and immediately makes it the active one — the common "add" path. */
    fun addProfile(name: String, avatarKey: String, ageRatingCap: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emitEvent(ProfileUiEvent.Error("Give this profile a name"))
            return
        }
        viewModelScope.launch {
            profileRepository.addProfile(trimmed, avatarKey, ageRatingCap)
                .onSuccess { id -> userPreferencesRepository.setActiveProfileId(id) }
                .onFailure { emitEvent(ProfileUiEvent.Error(it.toUserMessage())) }
        }
    }

    fun updateProfile(profile: ProfileEntity, name: String, avatarKey: String, ageRatingCap: String?) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) {
            emitEvent(ProfileUiEvent.Error("Give this profile a name"))
            return
        }
        viewModelScope.launch {
            profileRepository.update(profile.copy(name = trimmed, avatarKey = avatarKey, ageRatingCap = ageRatingCap))
        }
    }

    fun deleteProfile(profile: ProfileEntity) {
        viewModelScope.launch { profileRepository.delete(profile) }
    }

    fun selectActive(profileId: Long) {
        viewModelScope.launch { userPreferencesRepository.setActiveProfileId(profileId) }
    }

    /** PLAN.md §4 (M3d): selects the Family profile as active via the sentinel, exactly like [selectActive] does for a real person. */
    fun selectFamilyActive() {
        viewModelScope.launch { userPreferencesRepository.setActiveProfileId(FAMILY_PROFILE_SENTINEL_ID) }
    }

    /**
     * Create-or-edit (see [FamilyProfileRepository.save]'s kdoc). Surfaces a
     * [FamilyProfileMembersException] the same way [addProfile] surfaces
     * [MaxProfilesReachedException] — a one-shot [ProfileUiEvent.Error], never a crash.
     */
    fun saveFamilyProfile(name: String, avatarKey: String, memberProfileIds: List<Long>) {
        viewModelScope.launch {
            familyProfileRepository.save(name, avatarKey, memberProfileIds)
                .onFailure { emitEvent(ProfileUiEvent.Error(it.message ?: "Couldn't save the family profile")) }
        }
    }

    private fun emitEvent(event: ProfileUiEvent) {
        viewModelScope.launch { _events.emit(event) }
    }

    private fun Throwable.toUserMessage(): String = when (this) {
        is MaxProfilesReachedException -> "You've reached the 10-profile limit"
        else -> message ?: "Something went wrong"
    }
}

sealed interface ProfileUiEvent {
    data class Error(val message: String) : ProfileUiEvent
}
