package org.seg7.familywatchlist.ui.activity

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import org.seg7.familywatchlist.common.AppClock
import org.seg7.familywatchlist.data.repository.RefreshLogRepository
import org.seg7.familywatchlist.data.repository.RefreshRun
import org.seg7.familywatchlist.data.repository.UserPreferencesRepository
import org.seg7.familywatchlist.work.RefreshSlots

data class ActivityUiState(
    val header: String = "",
    val runs: List<RefreshRun> = emptyList(),
    val zone: ZoneId = ZoneId.systemDefault(),
    val loaded: Boolean = false,
)

/** PLAN.md §5d part 3 (M15): offline-first -- everything here is a Room/DataStore read. */
class ActivityViewModel(
    logRepository: RefreshLogRepository,
    prefs: UserPreferencesRepository,
    private val clock: AppClock,
    private val zone: ZoneId = ZoneId.systemDefault(),
) : ViewModel() {

    val uiState: StateFlow<ActivityUiState> = combine(
        logRepository.observeRuns(),
        logRepository.observeLastFinishedAt(),
        prefs.refreshDayOfWeek,
        prefs.refreshHour,
    ) { runs, lastFinished, day, hour ->
        val now = ZonedDateTime.ofInstant(Instant.ofEpochMilli(clock.nowMillis()), zone)
        ActivityUiState(
            header = ActivityFormat.header(lastFinished, RefreshSlots.nextSlot(now, day, hour), zone),
            runs = runs,
            zone = zone,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), ActivityUiState())
}
