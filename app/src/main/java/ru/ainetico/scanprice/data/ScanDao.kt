package ru.ainetico.scanprice.data

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface ScanDao {
  @Insert
  suspend fun insert(scan: Scan): Long

  @Update
  suspend fun update(scan: Scan)

  @Query("SELECT * FROM scans WHERE id = :id")
  suspend fun getById(id: Long): Scan?

  @Query("SELECT * FROM scans WHERE status = 'PROCESSING'")
  suspend fun getProcessingScans(): List<Scan>

  @Query("SELECT * FROM scans ORDER BY createdAt DESC")
  suspend fun getAllScans(): List<Scan>

  @Query("SELECT * FROM scans WHERE status != 'PROCESSING' ORDER BY createdAt DESC")
  fun getAllScansFlow(): Flow<List<Scan>>

  @Query("SELECT * FROM scans WHERE status != 'PROCESSING' ORDER BY createdAt DESC")
  fun getAllScansPaged(): PagingSource<Int, Scan>

  @Query("DELETE FROM scans WHERE id = :id")
  suspend fun deleteById(id: Long)
}
