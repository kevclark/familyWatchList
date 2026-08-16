package org.seg7.familywatchlist.common

/** Seam for TTL logic: repository tests inject a fake clock instead of depending on wall time. */
interface AppClock {
    fun nowMillis(): Long
}

class SystemAppClock : AppClock {
    override fun nowMillis(): Long = System.currentTimeMillis()
}
