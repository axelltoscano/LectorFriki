package com.panels.reader

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color as AndroidColor
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import com.github.junrar.Junrar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.zip.ZipFile

fun documentName(context: Context, uri: Uri): String =
    context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: uri.lastPathSegment?.substringAfterLast('/') ?: "Cómic"

class UniversalComicReader(
    private val context: Context,
    private val uri: Uri,
    private val comicId: String
) {
    private var archiveType = "CBZ"
    private var localArchiveFile: File? = null
    private var zipEntries: List<String> = emptyList()
    private var extractedRarFiles: List<File> = emptyList()
    private var totalPagesCount = 0

    suspend fun initialize(): Int = withContext(Dispatchers.IO) {
        val name = documentName(context, uri).lowercase()
        archiveType = when {
            name.endsWith(".pdf") -> "PDF"
            name.endsWith(".cbr") || name.endsWith(".rar") -> "CBR"
            else -> "CBZ"
        }

        val tempFile = File(context.cacheDir, "comic_cache_$comicId.tmp")
        if (!tempFile.exists() || tempFile.length() == 0L) {
            val staging = File(context.cacheDir, "comic_cache_$comicId.part")
            try {
                val input = context.contentResolver.openInputStream(uri) ?: error("No se puede abrir el archivo")
                input.use { source -> FileOutputStream(staging).use { source.copyTo(it) } }
                check(staging.length() > 0L) { "El archivo está vacío" }
                check(staging.renameTo(tempFile)) { "No se pudo guardar la copia temporal" }
            } finally {
                staging.delete()
            }
        }
        localArchiveFile = tempFile

        when (archiveType) {
            "PDF" -> {
                ParcelFileDescriptor.open(checkNotNull(localArchiveFile), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        totalPagesCount = renderer.pageCount
                    }
                }
            }
            "CBR" -> {
                val rarDir = File(context.cacheDir, "cbr_$comicId").apply { mkdirs() }
                if (rarDir.listFiles().isNullOrEmpty()) {
                    try { Junrar.extract(tempFile, rarDir) } catch (e: Exception) {
                        rarDir.deleteRecursively()
                        throw e
                    }
                }
                extractedRarFiles = rarDir.walkTopDown()
                    .filter { it.isFile && isImage(it.name) }
                    .sortedWith { a, b -> naturalCompare(a.name, b.name) }
                    .toList()
                totalPagesCount = extractedRarFiles.size
            }
            else -> {
                ZipFile(tempFile).use { zip ->
                    zipEntries = zip.entries().asSequence()
                        .filter { !it.isDirectory && isImage(it.name) }
                        .map { it.name }
                        .sortedWith { a, b -> naturalCompare(a, b) }
                        .toList()
                }
                totalPagesCount = zipEntries.size
            }
        }
        check(totalPagesCount > 0) { "El archivo no contiene páginas compatibles" }
        totalPagesCount
    }

    private fun decodeImage(open: () -> InputStream): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        open().use { BitmapFactory.decodeStream(it, null, bounds) }
        require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Imagen no compatible" }
        var sample = 1
        while (bounds.outWidth.toLong() / sample * (bounds.outHeight / sample) > 8_000_000L) sample *= 2
        return open().use {
            BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
        }
    }

    suspend fun loadPageBitmap(pageIndex: Int): Bitmap? = withContext(Dispatchers.IO) {
        if (pageIndex < 0 || pageIndex >= totalPagesCount) return@withContext null
        val rawBitmap: Bitmap? = when (archiveType) {
            "PDF" -> {
                ParcelFileDescriptor.open(checkNotNull(localArchiveFile), ParcelFileDescriptor.MODE_READ_ONLY).use { pfd ->
                    PdfRenderer(pfd).use { renderer ->
                        renderer.openPage(pageIndex).use { page ->
                            val factor = minOf(2.0, kotlin.math.sqrt(8_000_000.0 / (page.width.toDouble() * page.height)))
                            val bmp = Bitmap.createBitmap((page.width * factor).toInt().coerceAtLeast(1), (page.height * factor).toInt().coerceAtLeast(1), Bitmap.Config.ARGB_8888)
                            bmp.eraseColor(AndroidColor.WHITE)
                            page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                            bmp
                        }
                    }
                }
            }
            "CBR" -> {
                val f = extractedRarFiles.getOrNull(pageIndex) ?: return@withContext null
                decodeImage { f.inputStream() }
            }
            else -> {
                val file = localArchiveFile ?: return@withContext null
                ZipFile(file).use { zip ->
                    val entry = zip.getEntry(zipEntries[pageIndex]) ?: return@withContext null
                    decodeImage { zip.getInputStream(entry) }
                }
            }
        }
        rawBitmap
    }

    suspend fun extractCoverThumbnail(): String? = withContext(Dispatchers.IO) {
        val coverBmp = loadPageBitmap(0) ?: return@withContext null
        val coverFile = File(context.filesDir, "cover_$comicId.jpg")
        FileOutputStream(coverFile).use { out ->
            val ratio = coverBmp.height.toFloat() / coverBmp.width.toFloat()
            val thumbW = 420
            val thumbH = (thumbW * ratio).toInt().coerceAtLeast(1)
            val scaled = Bitmap.createScaledBitmap(coverBmp, thumbW, thumbH, true)
            scaled.compress(Bitmap.CompressFormat.JPEG, 85, out)
            if (scaled !== coverBmp) scaled.recycle()
        }
        coverBmp.recycle()
        coverFile.absolutePath
    }

    private fun isImage(name: String): Boolean {
        val n = name.lowercase()
        return (n.endsWith(".jpg") || n.endsWith(".jpeg") || n.endsWith(".png") || n.endsWith(".webp")) &&
                !n.contains("__macosx")
    }

    private fun naturalCompare(a: String, b: String): Int {
        val regex = Regex("(\\d+)|(\\D+)")
        val pA = regex.findAll(a).map { it.value }.toList()
        val pB = regex.findAll(b).map { it.value }.toList()
        for (i in 0 until minOf(pA.size, pB.size)) {
            val nA = pA[i].toBigIntegerOrNull()
            val nB = pB[i].toBigIntegerOrNull()
            val c = if (nA != null && nB != null) nA.compareTo(nB) else pA[i].compareTo(pB[i], true)
            if (c != 0) return c
        }
        return pA.size.compareTo(pB.size)
    }
}

