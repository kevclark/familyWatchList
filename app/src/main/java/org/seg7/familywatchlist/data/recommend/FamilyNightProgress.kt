package org.seg7.familywatchlist.data.recommend

/**
 * PLAN.md §5d part 5 (M15): the stages the ad-hoc Family Night blend reports while it works, in
 * place of a bare spinner. The labels are shown verbatim on Home (restrained copy, no emoji).
 */
sealed interface FamilyNightProgress {
    val label: String

    data object BuildingProfiles : FamilyNightProgress {
        override val label: String = "Building taste profiles…"
    }

    data object FindingCandidates : FamilyNightProgress {
        override val label: String = "Finding candidates…"
    }

    data class CheckingAvailability(val done: Int, val total: Int) : FamilyNightProgress {
        override val label: String = "Checking availability $done/$total…"
    }
}
