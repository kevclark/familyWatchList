package org.seg7.familywatchlist.data.repository

import android.content.Context
import android.net.Uri
import androidx.room.withTransaction
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.backup.BackupPayload
import org.seg7.familywatchlist.data.backup.FamilyProfileBackup
import org.seg7.familywatchlist.data.backup.NotificationPreferenceBackup
import org.seg7.familywatchlist.data.backup.ProfileBackup
import org.seg7.familywatchlist.data.backup.ProfileSlidersBackup
import org.seg7.familywatchlist.data.backup.RatingBackup
import org.seg7.familywatchlist.data.backup.UserPreferencesBackup
import org.seg7.familywatchlist.data.backup.WatchEventBackup
import org.seg7.familywatchlist.data.backup.WatchlistEntryBackup
import org.seg7.familywatchlist.data.local.AppDatabase
import org.seg7.familywatchlist.data.local.entity.FamilyProfileEntity
import org.seg7.familywatchlist.data.local.entity.FamilyProfileMemberEntity
import org.seg7.familywatchlist.data.local.entity.NotificationPreferenceEntity
import org.seg7.familywatchlist.data.local.entity.ProfileEntity
import org.seg7.familywatchlist.data.local.entity.ProfileSlidersEntity
import org.seg7.familywatchlist.data.local.entity.RatingEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventEntity
import org.seg7.familywatchlist.data.local.entity.WatchEventProfileEntity
import org.seg7.familywatchlist.data.local.entity.WatchlistEntryEntity

/**
 * PLAN.md §5 screen 8 "JSON backup/restore" (M4b). Assembles/restores [BackupPayload] — see its
 * kdoc for the exact scope (user data only, TMDB cache excluded) and versioning policy. SAF
 * plumbing (`ACTION_CREATE_DOCUMENT`/`ACTION_OPEN_DOCUMENT`) is the caller's job
 * ([org.seg7.familywatchlist.ui.settings.SettingsScreen]) — this class only knows how to turn a
 * [Uri] (already granted, already picked by the user) into bytes and back.
 */
class BackupRepository(
    private val database: AppDatabase,
    private val userPreferencesRepository: UserPreferencesRepository,
    private val clock: AppClock,
) {
    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = false
        encodeDefaults = true
    }

    sealed class RestoreOutcome {
        data object Success : RestoreOutcome()
        data class Error(val message: String) : RestoreOutcome()
    }

    /** Builds the exportable snapshot from Room + the portable subset of DataStore preferences. */
    suspend fun buildSnapshot(): BackupPayload {
        val profileDao = database.profileDao()
        val familyProfileDao = database.familyProfileDao()
        val watchEventDao = database.watchEventDao()
        val ratingDao = database.ratingDao()
        val watchlistDao = database.watchlistDao()
        val providerDao = database.providerDao()
        val slidersDao = database.profileSlidersDao()
        val notifDao = database.notificationPreferenceDao()

        val events = watchEventDao.getAllOnce()
        val tagsByEvent = watchEventDao.getAllTagsOnce().groupBy({ it.watchEventId }, { it.profileId })

        return BackupPayload(
            exportedAt = Instant.ofEpochMilli(clock.nowMillis()).toString(),
            profiles = profileDao.getAllOnce().map {
                ProfileBackup(
                    id = it.id,
                    name = it.name,
                    avatarKey = it.avatarKey,
                    ageRatingCap = it.ageRatingCap,
                    createdAt = it.createdAt,
                )
            },
            familyProfile = familyProfileDao.get()?.let {
                FamilyProfileBackup(name = it.name, avatarKey = it.avatarKey, createdAt = it.createdAt)
            },
            familyProfileMemberIds = familyProfileDao.getMemberIds(),
            watchEvents = events.map {
                WatchEventBackup(
                    id = it.id,
                    tmdbId = it.tmdbId,
                    mediaType = it.mediaType,
                    watchedAt = it.watchedAt.toString(),
                    note = it.note,
                    profileIds = tagsByEvent[it.id].orEmpty(),
                )
            },
            ratings = ratingDao.getAllOnce().map {
                RatingBackup(
                    profileId = it.profileId,
                    tmdbId = it.tmdbId,
                    mediaType = it.mediaType,
                    value = it.value,
                    ratedAt = it.ratedAt,
                )
            },
            watchlistEntries = watchlistDao.getAllOnce().map {
                WatchlistEntryBackup(
                    tmdbId = it.tmdbId,
                    mediaType = it.mediaType,
                    addedByProfileId = it.addedByProfileId,
                    addedAt = it.addedAt,
                    state = it.state,
                )
            },
            subscribedProviderIds = providerDao.getSubscribed().map { it.providerId },
            profileSliders = slidersDao.getAllOnce().map {
                ProfileSlidersBackup(
                    profileId = it.profileId,
                    discovery = it.discovery,
                    recency = it.recency,
                    personalMatch = it.personalMatch,
                    suggestionCount = it.suggestionCount,
                    eligibleCandidateCount = it.eligibleCandidateCount,
                )
            },
            notificationPreferences = notifDao.getAllOnce().map {
                NotificationPreferenceBackup(profileId = it.profileId, enabled = it.enabled)
            },
            userPreferences = UserPreferencesBackup(
                region = userPreferencesRepository.region.first(),
                notificationsEnabled = userPreferencesRepository.notificationsEnabled.first(),
                refreshDayOfWeek = userPreferencesRepository.refreshDayOfWeek.first().name,
                refreshHour = userPreferencesRepository.refreshHour.first(),
                familyBlendSlider = userPreferencesRepository.familyBlendSlider.first(),
            ),
        )
    }

    fun encode(payload: BackupPayload): String = json.encodeToString(BackupPayload.serializer(), payload)

    /** Writes the current snapshot to an already-picked SAF [uri] (`ACTION_CREATE_DOCUMENT`). */
    suspend fun exportTo(context: Context, uri: Uri) {
        val text = encode(buildSnapshot())
        val stream = context.contentResolver.openOutputStream(uri)
            ?: error("Couldn't open the chosen file for writing")
        stream.use { it.write(text.toByteArray(Charsets.UTF_8)) }
    }

    /**
     * Reads and validates an already-picked SAF [uri] (`ACTION_OPEN_DOCUMENT`), then restores it
     * if — and only if — it parses and passes [validate]. A malformed file, a wrong-version file,
     * or an unreadable one all return [RestoreOutcome.Error] with the existing database left
     * completely untouched; nothing here writes to Room until parsing and validation have both
     * already succeeded.
     */
    suspend fun importFrom(context: Context, uri: Uri): RestoreOutcome {
        val text = try {
            context.contentResolver.openInputStream(uri)?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }
                ?: return RestoreOutcome.Error("Couldn't open the chosen file")
        } catch (e: java.io.IOException) {
            return RestoreOutcome.Error("Couldn't read the chosen file: ${e.message}")
        }

        val payload = try {
            json.decodeFromString(BackupPayload.serializer(), text)
        } catch (e: SerializationException) {
            return RestoreOutcome.Error("That file isn't a valid Family Watchlist backup")
        } catch (e: IllegalArgumentException) {
            return RestoreOutcome.Error("That file isn't a valid Family Watchlist backup")
        }

        validate(payload)?.let { return RestoreOutcome.Error(it) }

        restore(payload)
        return RestoreOutcome.Success
    }

    /** Returns a human-readable error, or null if [payload] is safe to restore. */
    private fun validate(payload: BackupPayload): String? {
        if (payload.version != BackupPayload.CURRENT_VERSION) {
            return "This backup is from a different app version (backup v${payload.version}, " +
                "this app reads v${BackupPayload.CURRENT_VERSION}) and can't be restored"
        }
        val profileIds = payload.profiles.map { it.id }.toSet()
        if (profileIds.size != payload.profiles.size) return "Backup file is corrupt: duplicate profile ids"
        val refersToUnknownProfile = payload.watchEvents.any { event -> event.profileIds.any { it !in profileIds } } ||
            payload.ratings.any { it.profileId !in profileIds } ||
            payload.watchlistEntries.any { it.addedByProfileId !in profileIds } ||
            payload.profileSliders.any { it.profileId !in profileIds } ||
            payload.notificationPreferences.any { it.profileId !in profileIds } ||
            payload.familyProfileMemberIds.any { it !in profileIds }
        if (refersToUnknownProfile) return "Backup file is corrupt: references a profile it doesn't include"
        if (runCatching { DayOfWeek.valueOf(payload.userPreferences.refreshDayOfWeek) }.isFailure) {
            return "Backup file is corrupt: invalid refresh day"
        }
        if (payload.userPreferences.refreshHour !in 0..23) {
            return "Backup file is corrupt: invalid refresh hour"
        }
        return null
    }

    /**
     * Wipes and re-imports every user-data table in one Room transaction (all-or-nothing — a
     * failure partway through rolls back rather than leaving a half-restored database), then
     * applies the portable preferences afterwards (DataStore has no shared transaction with
     * Room, so this is deliberately the last step, after the Room half has already committed).
     */
    private suspend fun restore(payload: BackupPayload) {
        database.withTransaction {
            val profileDao = database.profileDao()
            val familyProfileDao = database.familyProfileDao()
            val watchEventDao = database.watchEventDao()
            val ratingDao = database.ratingDao()
            val watchlistDao = database.watchlistDao()
            val providerDao = database.providerDao()
            val slidersDao = database.profileSlidersDao()
            val notifDao = database.notificationPreferenceDao()

            // Wipe every table this payload owns, deepest-dependency-first isn't required here
            // (none of these have real FKs except family_profile_members, cleared explicitly).
            watchEventDao.deleteAllTags()
            watchEventDao.deleteAllEvents()
            ratingDao.deleteAll()
            watchlistDao.deleteAll()
            slidersDao.deleteAll()
            notifDao.deleteAll()
            familyProfileDao.deleteAllMembers()
            familyProfileDao.deleteFamilyProfile()
            profileDao.deleteAll()

            profileDao.insertAllPreservingIds(
                payload.profiles.map {
                    ProfileEntity(id = it.id, name = it.name, avatarKey = it.avatarKey, ageRatingCap = it.ageRatingCap, createdAt = it.createdAt)
                },
            )
            payload.familyProfile?.let {
                familyProfileDao.upsert(
                    FamilyProfileEntity(
                        id = FamilyProfileEntity.SINGLETON_ID,
                        name = it.name,
                        avatarKey = it.avatarKey,
                        createdAt = it.createdAt,
                    ),
                )
            }
            if (payload.familyProfileMemberIds.isNotEmpty()) {
                familyProfileDao.insertMembers(payload.familyProfileMemberIds.map { FamilyProfileMemberEntity(it) })
            }
            watchEventDao.insertEventsPreservingIds(
                payload.watchEvents.map {
                    WatchEventEntity(id = it.id, tmdbId = it.tmdbId, mediaType = it.mediaType, watchedAt = LocalDate.parse(it.watchedAt), note = it.note)
                },
            )
            watchEventDao.insertTags(
                payload.watchEvents.flatMap { event -> event.profileIds.map { WatchEventProfileEntity(watchEventId = event.id, profileId = it) } },
            )
            ratingDao.upsertAll(
                payload.ratings.map { RatingEntity(profileId = it.profileId, tmdbId = it.tmdbId, mediaType = it.mediaType, value = it.value, ratedAt = it.ratedAt) },
            )
            watchlistDao.upsertAll(
                payload.watchlistEntries.map { WatchlistEntryEntity(tmdbId = it.tmdbId, mediaType = it.mediaType, addedByProfileId = it.addedByProfileId, addedAt = it.addedAt, state = it.state) },
            )
            slidersDao.upsertAll(
                payload.profileSliders.map {
                    ProfileSlidersEntity(
                        profileId = it.profileId,
                        discovery = it.discovery,
                        recency = it.recency,
                        personalMatch = it.personalMatch,
                        suggestionCount = it.suggestionCount,
                        eligibleCandidateCount = it.eligibleCandidateCount,
                    )
                },
            )
            notifDao.upsertAll(
                payload.notificationPreferences.map { NotificationPreferenceEntity(profileId = it.profileId, enabled = it.enabled) },
            )
            // Providers themselves are TMDB cache, seeded independently — only re-apply which
            // (already-seeded) ids are subscribed. An id from the backup that hasn't been seeded
            // yet on this install is silently skipped; it'll simply read as unsubscribed until
            // the provider catalog refetches and the user can re-toggle it in Settings.
            providerDao.clearAllSubscribed()
            payload.subscribedProviderIds.forEach { providerDao.setSubscribed(it, true) }
        }

        userPreferencesRepository.setRegion(payload.userPreferences.region)
        userPreferencesRepository.setNotificationsEnabled(payload.userPreferences.notificationsEnabled)
        userPreferencesRepository.setRefreshSchedule(
            DayOfWeek.valueOf(payload.userPreferences.refreshDayOfWeek),
            payload.userPreferences.refreshHour,
        )
        userPreferencesRepository.setFamilyBlendSlider(payload.userPreferences.familyBlendSlider)
    }
}
