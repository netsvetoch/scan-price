package ru.ainetico.honestprice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StoreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(store: Store): Long

    @Query("SELECT * FROM stores ORDER BY name ASC")
    suspend fun getAllStores(): List<Store>

    @Query("SELECT * FROM stores WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun search(query: String): List<Store>
}
