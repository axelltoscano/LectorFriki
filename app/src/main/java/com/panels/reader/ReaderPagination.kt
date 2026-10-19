package com.panels.reader

/** Each page occurs once. When offset is false the cover is shown on its own. */
fun buildSpreads(pageCount: Int, doublePage: Boolean, offset: Boolean): List<Pair<Int, Int?>> {
    require(pageCount > 0)
    val result = mutableListOf<Pair<Int, Int?>>()
    var page = 0
    if (doublePage && !offset) {
        result.add(0 to null)
        page = 1
    }
    while (page < pageCount) {
        val second = if (doublePage && page + 1 < pageCount) page + 1 else null
        result.add(page to second)
        page += if (second != null) 2 else 1
    }
    return result
}
