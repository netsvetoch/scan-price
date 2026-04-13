package ru.ainetico.honestprice.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
  tableName = "scans",
  indices = [
    Index(value = ["createdAt"]),
    Index(value = ["status"])
  ]
)
data class Scan(
  @PrimaryKey(autoGenerate = true)
  val id: Long = 0,
  val status: ScanStatus = ScanStatus.PROCESSING,
  val imagePath: String? = null,
  val thumbnailPath: String? = null,
  val productName: String? = null,
  val productDescription: String? = null,
  val priceRegular: String? = null,
  val priceDiscount: String? = null,
  val weightValue: String? = null,
  val weightUnit: String? = null,
  val barcode: String? = null,
  val storeName: String? = null,
  val latitude: Double? = null,
  val longitude: Double? = null,
  val pricePerUnit: String? = null,
  val pricePerUnitDiscount: String? = null,
  val displayUnit: String? = null,
  val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
  val createdAt: Long = System.currentTimeMillis()
)
