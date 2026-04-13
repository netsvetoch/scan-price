package ru.ainetico.scanprice.ui.history

import ru.ainetico.scanprice.data.Scan

sealed interface ScanListItem {
  data class ScanItem(val scan: Scan) : ScanListItem
  data class DateHeader(val dateKey: String, val timestamp: Long) : ScanListItem
}
