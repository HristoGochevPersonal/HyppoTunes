package com.example.hyppotunes.presentation.search

import android.content.SharedPreferences
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.lang.reflect.Type


object SearchHistoryDataSource {
    var sharedPreferences: SharedPreferences? = null

    fun fetch(): List<SearchHistoryItem> {
        val preferences = sharedPreferences ?: return emptyList()
        val serializedObject =
            preferences.getString("search_history", null) ?: return emptyList()
        val gson = Gson()
        val type: Type = object : TypeToken<List<SearchHistoryItem>>() {}.type
        return gson.fromJson(serializedObject, type)
    }

    fun save(searchHistory: List<SearchHistoryItem>): Boolean {
        val preferences = sharedPreferences ?: return false

        val gson = Gson()
        val json = gson.toJson(searchHistory)

        return preferences.edit().apply {
            putString("search_history", json)
        }.commit()
    }

    fun clear(): Boolean {
        val preferences = sharedPreferences ?: return false
        return preferences.edit().remove("search_history").commit()
    }
}