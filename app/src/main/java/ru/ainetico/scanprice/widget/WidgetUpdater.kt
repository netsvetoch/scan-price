package ru.ainetico.honestprice.widget

import android.content.Context
import androidx.glance.appwidget.updateAll

suspend fun updateLastScanWidget(context: Context) {
  LastScanWidget().updateAll(context)
}
