package ru.ainetico.honestprice.ui.history

import ru.ainetico.honestprice.data.Scan

sealed interface ScanListItem {
  data class ScanItem(val scan: Scan) : ScanListItem
  data class DateHeader(val dateKey: String, val timestamp: Long) : ScanListItem
}
