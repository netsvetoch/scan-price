package ru.ainetico.honestprice.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import ru.ainetico.honestprice.data.Scan
import ru.ainetico.honestprice.data.ScanRepository

class HistoryViewModel(
  scanRepository: ScanRepository
) : ViewModel() {
  // null = loading, empty list = loaded but no scans
  val scans: StateFlow<List<Scan>?> = scanRepository.getAllScansFlow()
    .map<List<Scan>, List<Scan>?> { it }
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)
}
