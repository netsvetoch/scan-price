package ru.ainetico.scanprice.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import ru.ainetico.scanprice.data.ScanRepository
import java.util.Calendar

private fun dateKey(timestamp: Long): String {
  val cal = Calendar.getInstance().apply { timeInMillis = timestamp }
  return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.DAY_OF_YEAR)}"
}

@dagger.hilt.android.lifecycle.HiltViewModel
class HistoryViewModel @javax.inject.Inject constructor(
  scanRepository: ScanRepository
) : ViewModel() {

  val scansPaged: Flow<PagingData<ScanListItem>> = Pager(
    config = PagingConfig(pageSize = 30, prefetchDistance = 10),
    pagingSourceFactory = { scanRepository.getAllScansPaged() }
  ).flow
    .map { pagingData -> pagingData.map { ScanListItem.ScanItem(it) } }
    .map { pagingData ->
      pagingData.insertSeparators { before: ScanListItem.ScanItem?, after: ScanListItem.ScanItem? ->
        val afterDate = after?.let { dateKey(it.scan.createdAt) }
        val beforeDate = before?.let { dateKey(it.scan.createdAt) }
        when {
          after != null && beforeDate != afterDate ->
            ScanListItem.DateHeader(afterDate!!, after.scan.createdAt)

          else -> null
        }
      }
    }
    .cachedIn(viewModelScope)
}
