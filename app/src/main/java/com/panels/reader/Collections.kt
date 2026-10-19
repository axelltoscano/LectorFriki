package com.panels.reader


data class BookCollection(val id: String, val name: String, val bookIds: Set<String> = emptySet())

object CollectionRules {
    fun validName(name: String, collections: List<BookCollection>, exceptId: String? = null): Boolean =
        name.trim().isNotEmpty() && name.trim().length <= 60 &&
            collections.none { it.id != exceptId && it.name.equals(name.trim(), ignoreCase = true) }

    fun assign(collections: List<BookCollection>, bookId: String, selectedIds: Set<String>): List<BookCollection> =
        collections.map {
            it.copy(bookIds = if (it.id in selectedIds) it.bookIds + bookId else it.bookIds - bookId)
        }

    fun withoutBook(collections: List<BookCollection>, bookId: String): List<BookCollection> =
        collections.map { it.copy(bookIds = it.bookIds - bookId) }
}

