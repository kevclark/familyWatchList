package org.seg7.familywatchlist.testutil

import org.seg7.familywatchlist.common.AppClock

class FakeClock(startMillis: Long = 0L) : AppClock {
    var current: Long = startMillis
    override fun nowMillis(): Long = current
    fun advanceBy(millis: Long) {
        current += millis
    }
}
