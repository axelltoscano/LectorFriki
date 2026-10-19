package com.panels.reader

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

object CollectionStorage {
    private const val PREFS = "panels_library_prefs"
    private const val KEY = "collections_v1"

    fun load(context: Context): List<BookCollection> {
        val raw = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, "[]") ?: "[]"
        val array = JSONArray(raw)
        return (0 until array.length()).map { index ->
            val obj = array.getJSONObject(index)
            val books = obj.optJSONArray("bookIds") ?: JSONArray()
            BookCollection(obj.getString("id"), obj.getString("name"),
                (0 until books.length()).map { books.getString(it) }.toSet())
        }
    }

    fun save(context: Context, collections: List<BookCollection>) {
        val array = JSONArray()
        collections.forEach { collection ->
            array.put(JSONObject().apply {
                put("id", collection.id)
                put("name", collection.name)
                put("bookIds", JSONArray(collection.bookIds.toList()))
            })
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putString(KEY, array.toString()).apply()
    }
}
