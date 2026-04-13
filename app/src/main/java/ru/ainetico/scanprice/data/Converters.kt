package ru.ainetico.scanprice.data

import android.util.Log
import androidx.room.TypeConverter

class Converters {
  @TypeConverter
  fun fromScanStatus(value: ScanStatus): String = value.name

  @TypeConverter
  fun toScanStatus(value: String): ScanStatus =
    ScanStatus.entries.find { it.name == value }
      ?: ScanStatus.PROCESSING.also {
        Log.w("Converters", "Unknown ScanStatus: $value, defaulting to PROCESSING")
      }

  @TypeConverter
  fun fromSyncStatus(value: SyncStatus): String = value.name

  @TypeConverter
  fun toSyncStatus(value: String): SyncStatus =
    SyncStatus.entries.find { it.name == value }
      ?: SyncStatus.LOCAL_ONLY.also {
        Log.w("Converters", "Unknown SyncStatus: $value, defaulting to LOCAL_ONLY")
      }
}
