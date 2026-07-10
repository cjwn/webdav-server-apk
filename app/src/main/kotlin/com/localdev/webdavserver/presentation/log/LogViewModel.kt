package com.localdev.webdavserver.presentation.log

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.localdev.webdavserver.data.local.model.LogEntry
import com.localdev.webdavserver.di.AppModule
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn

enum class LogFilter { ALL, GET, PUT, DELETE, ERRORS }

class LogViewModel : ViewModel() {

    val entries: StateFlow<List<LogEntry>> = AppModule.logRepository.entries
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _filter = MutableStateFlow(LogFilter.ALL)
    val filter: StateFlow<LogFilter> = _filter.asStateFlow()

    private val _autoScroll = MutableStateFlow(true)
    val autoScroll: StateFlow<Boolean> = _autoScroll.asStateFlow()

    fun setFilter(f: LogFilter) { _filter.value = f }
    fun toggleAutoScroll() { _autoScroll.value = !_autoScroll.value }
    fun clear() = AppModule.logRepository.clear()
    fun markRead() = AppModule.logRepository.markErrorsRead()

    fun apply(list: List<LogEntry>): List<LogEntry> = when (_filter.value) {
        LogFilter.ALL -> list
        LogFilter.GET -> list.filter { it.method.equals("GET", true) }
        LogFilter.PUT -> list.filter { it.method.equals("PUT", true) }
        LogFilter.DELETE -> list.filter { it.method.equals("DELETE", true) }
        LogFilter.ERRORS -> list.filter { it.isError }
    }
}
