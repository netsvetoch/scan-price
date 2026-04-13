package ru.ainetico.scanprice.util

import android.content.Context
import ru.ainetico.scanprice.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatRelativeDate(context: Context, timestamp: Long): String {
  val scanCal = Calendar.getInstance().apply { timeInMillis = timestamp }
  val todayCal = Calendar.getInstance()

  val sameDay = scanCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
          scanCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
  if (sameDay) return context.getString(R.string.date_today)

  todayCal.add(Calendar.DAY_OF_YEAR, -1)
  val yesterday = scanCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
          scanCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
  if (yesterday) return context.getString(R.string.date_yesterday)

  return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(timestamp))
}
