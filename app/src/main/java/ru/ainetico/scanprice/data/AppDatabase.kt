package ru.ainetico.honestprice.data

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Scan::class, Store::class], version = 3)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
  abstract fun scanDao(): ScanDao
  abstract fun storeDao(): StoreDao

  companion object {
    val MIGRATION_1_2 = object : Migration(1, 2) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE scans ADD COLUMN productDescription TEXT")
      }
    }

    val MIGRATION_2_3 = object : Migration(2, 3) {
      override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scans_createdAt ON scans (createdAt)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_scans_status ON scans (status)")
      }
    }
  }
}
