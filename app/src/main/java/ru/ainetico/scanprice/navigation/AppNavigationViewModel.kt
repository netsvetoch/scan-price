package ru.ainetico.scanprice.navigation

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import ru.ainetico.scanprice.data.Scan
import ru.ainetico.scanprice.model.AnalysisResult

data class AppNavigationState(
  val pendingResult: Pair<Long, AnalysisResult>? = null,
  val pendingScan: Scan? = null,
  val showCameraSheet: Boolean = false
)

class AppNavigationViewModel(
  initialShowCamera: Boolean = false
) : ViewModel() {

  private val _state = MutableStateFlow(AppNavigationState(showCameraSheet = initialShowCamera))
  val state: StateFlow<AppNavigationState> = _state

  fun setPendingResult(scanId: Long, result: AnalysisResult) {
    _state.update { it.copy(pendingResult = Pair(scanId, result)) }
  }

  fun clearPendingResult() {
    _state.update { it.copy(pendingResult = null) }
  }

  fun setPendingScan(scan: Scan?) {
    _state.update { it.copy(pendingScan = scan) }
  }

  fun setShowCameraSheet(show: Boolean) {
    _state.update { it.copy(showCameraSheet = show) }
  }
}
