package com.dalbear.onesync.ui

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.dalbear.onesync.OneSyncApp
import com.dalbear.onesync.data.HistoryRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class HistoryViewModel(application: Application) : AndroidViewModel(application) {

    private val repo = (application as OneSyncApp).container.repo

    var events by mutableStateOf<List<HistoryRow>>(emptyList())
        private set

    var loading by mutableStateOf(false)
        private set

    fun load() {
        viewModelScope.launch {
            loading = true
            events = withContext(Dispatchers.IO) { repo.recentHistory() }
            loading = false
        }
    }
}
