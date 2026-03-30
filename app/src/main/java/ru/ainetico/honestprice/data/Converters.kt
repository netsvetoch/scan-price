package ru.ainetico.honestprice.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromScanStatus(value: ScanStatus): String = value.name

    @TypeConverter
    fun toScanStatus(value: String): ScanStatus = ScanStatus.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
