package com.panels.reader

fun main() {
    check(buildSpreads(1, true, false) == listOf(0 to null))
    check(buildSpreads(6, true, false) == listOf(0 to null, 1 to 2, 3 to 4, 5 to null))
    check(buildSpreads(5, true, true) == listOf(0 to 1, 2 to 3, 4 to null))
    check(buildSpreads(3, false, true) == listOf(0 to null, 1 to null, 2 to null))
    var cases = 0
    for (count in 1..1000) for (double in listOf(false, true)) for (offset in listOf(false, true)) {
        val spreads = buildSpreads(count, double, offset)
        val pages = spreads.flatMap { listOfNotNull(it.first, it.second) }
        check(pages == (0 until count).toList()) { "Missing, repeated or reordered pages" }
        for (page in 0 until count) {
            val spread = spreads.last { it.first <= page }
            check(spread.first == page || spread.second == page)
        }
        cases++
    }
    println("PASS: " + cases + " pagination configurations and boundary cases")
}
