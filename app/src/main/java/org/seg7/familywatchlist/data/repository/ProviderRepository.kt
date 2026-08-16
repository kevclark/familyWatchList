package org.seg7.familywatchlist.data.repository

import kotlinx.coroutines.flow.Flow
import org.seg7.familywatchlist.data.local.dao.ProviderDao
import org.seg7.familywatchlist.data.local.entity.ProviderEntity
import org.seg7.familywatchlist.data.remote.TmdbApi
import org.seg7.familywatchlist.data.remote.dto.WatchProviderDto

/** PLAN.md §2/§3: Provider — seeded once from TMDB's GB provider list; subscribed is toggled in Settings. */
class ProviderRepository(
    private val providerDao: ProviderDao,
    private val api: TmdbApi,
) {
    fun observeAll(): Flow<List<ProviderEntity>> = providerDao.observeAll()

    fun observeSubscribed(): Flow<List<ProviderEntity>> = providerDao.observeSubscribed()

    suspend fun getSubscribedIds(): List<Int> = providerDao.getSubscribed().map { it.providerId }

    /** Onboarding calls this once; a no-op if the catalog is already seeded. */
    suspend fun seedIfEmpty() {
        if (providerDao.count() > 0) return
        val movieProviders = api.movieProviders().results
        val tvProviders = api.tvProviders().results
        val merged = (movieProviders + tvProviders).distinctBy { it.providerId }
        providerDao.upsertAll(merged.map { it.toEntity() })
    }

    suspend fun setSubscribed(providerId: Int, subscribed: Boolean) = providerDao.setSubscribed(providerId, subscribed)

    private fun WatchProviderDto.toEntity(): ProviderEntity = ProviderEntity(
        providerId = providerId,
        name = providerName,
        logoPath = logoPath,
        subscribed = false,
        displayPriority = displayPriority,
    )
}
