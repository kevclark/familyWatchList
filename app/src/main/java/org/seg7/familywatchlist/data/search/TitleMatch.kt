package org.seg7.familywatchlist.data.search

/**
 * PLAN.md §5f: a real title that happens to contain genre words ("star wars", "family guy") must stay
 * reachable. A structured search also runs the title search, and results whose name equals or starts
 * with the query are shown in a small section above.
 */
object TitleMatch {
    const val MAX_MATCHES = 3

    /** Lowercase, punctuation stripped, "&" read as "and". */
    fun normalise(text: String): String =
        text.lowercase()
            .replace("'", "").replace("’", "")
            .replace("&", " and ")
            .map { if (it.isLetterOrDigit()) it else ' ' }
            .joinToString("")
            .split(' ')
            .filter { it.isNotEmpty() }
            .joinToString(" ")

    fun matches(title: String, query: String): Boolean {
        val q = normalise(query)
        if (q.isEmpty()) return false
        val t = normalise(title)
        return t == q || t.startsWith(q)
    }
}
