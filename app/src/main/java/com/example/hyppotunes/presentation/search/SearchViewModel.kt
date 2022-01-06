package com.example.hyppotunes.presentation.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.*

class SearchViewModel : ViewModel() {
    private val _stateFlow = MutableStateFlow(SearchHistoryDataSource.fetch().reversed())
    val stateFlow = _stateFlow.asStateFlow()

    fun triggerStateFlow() {
        viewModelScope.launch(Dispatchers.IO) {
            val searchHistory = SearchHistoryDataSource.fetch().reversed()
            _stateFlow.emit(searchHistory)
        }
    }

    fun update(keyword: String) {
        viewModelScope.launch(Dispatchers.IO) {
            if (keyword.isEmpty()) {
                return@launch
            }

            val newSearchHistoryItem = SearchHistoryItem(keyword)
            val searchHistory = SearchHistoryDataSource.fetch().toMutableList()

            if (!searchHistory.remove(newSearchHistoryItem)) {
                if (searchHistory.size > 5) {
                    searchHistory.removeFirst()
                }
            }

            searchHistory.add(newSearchHistoryItem)
            SearchHistoryDataSource.save(searchHistory)
            val fetched = SearchHistoryDataSource.fetch().reversed()
            _stateFlow.emit(fetched)
        }
    }

    fun clear() {
        viewModelScope.launch(Dispatchers.IO) {
            SearchHistoryDataSource.clear()
            _stateFlow.emit(listOf())
        }
    }
}