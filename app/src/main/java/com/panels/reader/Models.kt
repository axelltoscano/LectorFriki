package com.panels.reader


enum class ReadingMode(val label: String) {
    RIGHT_TO_LEFT("Manga (Der ← Izq)"),
    LEFT_TO_RIGHT("Cómic (Izq → Der)"),
    VERTICAL_CONTINUOUS("Webtoon (Vertical)")
}

enum class PageFit(val label: String) {
    FIT_SCREEN("Pantalla"),
    FIT_WIDTH("Ancho"),
    FIT_HEIGHT("Alto"),
    FILL_SCREEN("Llenar")
}

data class ReaderSettings(
    val readingMode: ReadingMode = ReadingMode.RIGHT_TO_LEFT,
    val pageFit: PageFit = PageFit.FIT_SCREEN,
    val doublePageInLandscape: Boolean = true,
    val offsetDoublePage: Boolean = false,
    val brightnessOverlay: Float = 0f,
    val warmFilter: Boolean = false
)

data class ComicBook(
    val id: String,
    val title: String,
    val fileUriString: String,
    val coverPath: String?,
    val totalPages: Int,
    val currentPage: Int = 0
) {
    val progress: Float
        get() = if (totalPages > 0) (currentPage + 1).toFloat() / totalPages else 0f
}

