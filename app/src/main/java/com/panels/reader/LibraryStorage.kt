package com.panels.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object LibraryStorage {
    private const val PREFS_NAME = "panels_library_prefs"
    private const val KEY_COMICS = "saved_comics_json"

    fun saveComics(context: Context, comics: List<ComicBook>) {
        val array = JSONArray()
        comics.forEach { comic ->
            val obj = JSONObject().apply {
                put("id", comic.id)
                put("title", comic.title)
                put("uri", comic.fileUriString)
                put("coverPath", comic.coverPath ?: "")
                put("totalPages", comic.totalPages)
                put("currentPage", comic.currentPage)
            }
            array.put(obj)
        }
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_COMICS, array.toString())
            .apply()
    }

    fun loadComics(context: Context): List<ComicBook> {
        val raw = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_COMICS, null) ?: return emptyList()
        val list = mutableListOf<ComicBook>()
        try {
            val array = JSONArray(raw)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                list.add(
                    ComicBook(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        fileUriString = obj.getString("uri"),
                        coverPath = obj.optString("coverPath").takeIf { it.isNotEmpty() },
                        totalPages = obj.getInt("totalPages"),
                        currentPage = obj.optInt("currentPage", 0)
                    )
                )
            }
        } catch (_: Exception) {}
        return list
    }
}

