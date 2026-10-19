package com.panels.reader

fun main() {
    val original = listOf(BookCollection("m", "Manga", setOf("existing")), BookCollection("p", "Pendientes"))
    check(!CollectionRules.validName("   ", original))
    check(!CollectionRules.validName(" manga ", original))
    check(CollectionRules.validName("Manga", original, "m"))
    check(CollectionRules.validName(" Favoritos ", original))
    check(!CollectionRules.validName("a".repeat(61), original))
    val both = CollectionRules.assign(original, "new", setOf("m", "p"))
    check(both.all { "new" in it.bookIds })
    check("existing" in both.first().bookIds)
    check(original.none { "new" in it.bookIds })
    val one = CollectionRules.assign(both, "new", setOf("p"))
    check("new" !in one.first().bookIds && "new" in one.last().bookIds)
    val none = CollectionRules.assign(one, "new", emptySet())
    check(none.none { "new" in it.bookIds })
    val removed = CollectionRules.withoutBook(both, "new")
    check(removed == original)
    val renamed = both.map { if (it.id == "m") it.copy(name = "Manga favorito") else it }
    check(renamed.first().bookIds == both.first().bookIds)
    val deletedCollection = renamed.filterNot { it.id == "m" }
    check(deletedCollection.single().bookIds == setOf("new"))
    println("PASS: validation, multi-collection membership, reassignment, removal and non-mutating updates")
}
