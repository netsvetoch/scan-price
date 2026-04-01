package ru.ainetico.honestprice.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun formatRelativeDate(timestamp: Long): String {
    val scanCal = Calendar.getInstance().apply { timeInMillis = timestamp }
    val todayCal = Calendar.getInstance()

    val sameDay = scanCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
        scanCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Сегодня"

    todayCal.add(Calendar.DAY_OF_YEAR, -1)
    val yesterday = scanCal.get(Calendar.YEAR) == todayCal.get(Calendar.YEAR) &&
        scanCal.get(Calendar.DAY_OF_YEAR) == todayCal.get(Calendar.DAY_OF_YEAR)
    if (yesterday) return "Вчера"

    return SimpleDateFormat("d MMMM yyyy", Locale("ru")).format(Date(timestamp))
}
