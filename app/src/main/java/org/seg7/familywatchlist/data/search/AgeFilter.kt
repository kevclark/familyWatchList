package org.seg7.familywatchlist.data.search

import org.seg7.familywatchlist.data.recommend.FamilyBlend

/** One chip in the search screen's age row. [cap] is the BBFC string it filters to; null is "Any". */
data class AgeOption(val label: String, val cap: String?, val tmdbCertification: String?)

enum class AgeVerdict { SHOW, SHOW_UNRATED, HIDE }

/**
 * PLAN.md §5f item 4. Ranking comes from the shared [FamilyBlend] (which owns `CERT_RANK`, M12
 * equivalents included); nothing here duplicates it.
 */
object AgeFilter {
    val ANY = AgeOption("Any", cap = null, tmdbCertification = null)

    /**
     * TMDB's GB movie certification order is U, PG, 12, 12A, 15, 18, so "12 or under" must be sent as
     * `12A` or `certification.lte=12` would drop 12A films.
     */
    val options: List<AgeOption> = listOf(
        AgeOption("U", "U", "U"),
        AgeOption("PG", "PG", "PG"),
        AgeOption("12", "12", "12A"),
        AgeOption("15", "15", "15"),
        AgeOption("18", "18", "18"),
        ANY,
    )

    /** The chip for a profile's cap (BBFC or an M12 equivalent such as 13+); no or unknown cap means Any. */
    fun optionForCap(cap: String?): AgeOption {
        val rank = cap?.let { FamilyBlend.certRank(it) } ?: return ANY
        return options.first { it.cap != null && FamilyBlend.certRank(it.cap) == rank }
    }

    /**
     * Unrated titles (null or unrecognised certification) are hidden at 12 or below and shown,
     * labelled, otherwise. A rated title over the cap is hidden.
     */
    fun evaluate(certification: String?, cap: String?): AgeVerdict {
        val capRank = cap?.let { FamilyBlend.certRank(it) }
        val rank = certification?.let { FamilyBlend.certRank(it) }
        return when {
            rank == null -> if (capRank != null && capRank <= UNRATED_HIDDEN_AT_OR_BELOW) AgeVerdict.HIDE else AgeVerdict.SHOW_UNRATED
            capRank != null && rank > capRank -> AgeVerdict.HIDE
            else -> AgeVerdict.SHOW
        }
    }

    private val UNRATED_HIDDEN_AT_OR_BELOW: Int = FamilyBlend.certRank("12")!!
}
