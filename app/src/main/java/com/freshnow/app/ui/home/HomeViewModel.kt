package com.freshnow.app.ui.home

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.freshnow.app.data.ScanRecordRepository
import com.freshnow.app.data.local.ScanRecord
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = ScanRecordRepository(application)

    val records: StateFlow<List<ScanRecord>> = repository.records
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(STOP_TIMEOUT_MS), emptyList())

    private companion object {
        const val STOP_TIMEOUT_MS = 5_000L
    }
}
