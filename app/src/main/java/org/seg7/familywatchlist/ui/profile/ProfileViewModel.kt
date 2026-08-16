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
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.repository.MaxProfilesReachedException
import org.seg7.familywatchlist.data.repository.ProfileRepository
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository

/**
 * Backs the profile picker (PLAN.md §5 screen 2): the avatar grid, add/edit/delete, and
 * setting the "active profile" DataStore flag. Cap enforcement (10) lives in
 * [ProfileRepository] — this ViewModel just surfaces the failure as a one-shot UI event.
 */
class ProfileViewModel(
    private val profileRepository: ProfileRepository,
    private val userPreferencesRepository: UserPreferencesRepository,
) : ViewModel() {

    val profiles: StateFlow<List<ProfileEntity>> = profileRepository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val isAtProfileCap: StateFlow<Boolean> = profiles
        .map { it.size >= ProfileRepository.MAX_PROFILES }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

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
