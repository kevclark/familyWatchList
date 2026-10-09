package org.seg7.familywatchlist.data.recommend

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.seg7.familywatchlist.data.local.entity.MediaType

/** PLAN.md §5e (M16): the pure paging rules behind "Show 30 more". */
class ExtraPicksTest {
    private fun keys(range: IntRange) = range.map { TitleKey(it, MediaType.MOVIE) }

    @Test
    fun `pages walk the ranked rest in order with correct offsets and no gaps or repeats`() {
        val pager = ExtraPicksPager(keys(31..100))

        val (first, p1) = pager.next()
        val (second, p2) = p1.next()
        val (third, p3) = p2.next()

        assertEquals(keys(31..60), first)
        assertEquals(keys(61..90), second)
        assertEquals(keys(91..100), third)
        assertEquals(emptyList<TitleKey>(), p3.remaining())
    }

    @Test
    fun `excluded titles (already shown or dismissed) are skipped without leaving a short page`() {
        val pager = ExtraPicksPager(keys(31..100))
        val excluded = keys(31..35).toSet() + keys(40..40)

        val (page, _) = pager.next(excluded)

        assertEquals(30, page.size)
        assertTrue(page.none { it in excluded })
        assertEquals(36, page.first().tmdbId)
        assertEquals(listOf(36, 37, 38, 39), page.take(4).map { it.tmdbId })
        assertEquals(41, page[4].tmdbId)
    }

    @Test
    fun `remaining honours exclusions so the card counts only titles that can still appear`() {
        val pager = ExtraPicksPager(keys(31..70))

        assertEquals(40, pager.remaining().size)
        assertEquals(37, pager.remaining(keys(31..33).toSet()).size)
        val (_, advanced) = pager.next()
        assertEquals(10, advanced.remaining().size)
    }

    @Test
    fun `rewound goes back to the first page`() {
        val (_, advanced) = ExtraPicksPager(keys(31..100)).next()

        assertEquals(keys(31..60), advanced.rewound().next().first)
    }

    @Test
    fun `an exhausted pager yields an empty page`() {
        val (_, done) = ExtraPicksPager(keys(31..40)).next()

        assertEquals(emptyList<TitleKey>(), done.next().first)
    }

    @Test
    fun `end card ranges and totals reflect what the next tap would add`() {
        val card = MoreCardState.of(visibleCount = 30, remaining = 264, loading = false)!!
        assertEquals("31–60 of 294", card.rangeLabel)

        val last = MoreCardState.of(visibleCount = 60, remaining = 12, loading = true)!!
        assertEquals("61–72 of 72", last.rangeLabel)
        assertTrue(last.loading)
    }

    @Test
    fun `no end card once nothing remains`() {
        assertNull(MoreCardState.of(visibleCount = 30, remaining = 0, loading = false))
    }
}
