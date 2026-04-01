package ru.ainetico.honestprice.data

import kotlinx.coroutines.flow.Flow
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.PriceResult

interface ScanRepository {
    suspend fun createProcessing(imagePath: String): Long
    suspend fun markCompleted(scanId: Long, tag: ParsedPriceTag, price: PriceResult?)
    suspend fun getProcessingScans(): List<Scan>
    suspend fun createManual(): Long
    suspend fun updateUserFields(
        scanId: Long,
        tag: ParsedPriceTag,
        price: PriceResult?,
        storeName: String?,
        latitude: Double?,
        longitude: Double?
    )
    fun getAllScansFlow(): Flow<List<Scan>>
    suspend fun getById(scanId: Long): Scan?
    suspend fun delete(scanId: Long)
    suspend fun getAllScans(): List<Scan>
}

class ScanRepositoryImpl(private val scanDao: ScanDao) : ScanRepository {

    override suspend fun createProcessing(imagePath: String): Long {
        return scanDao.insert(Scan(imagePath = imagePath))
    }

    override suspend fun markCompleted(scanId: Long, tag: ParsedPriceTag, price: PriceResult?) {
        val existing = scanDao.getById(scanId) ?: return
        scanDao.update(
            existing.copy(
                status = ScanStatus.COMPLETED,
                productName = tag.productName,
                priceRegular = tag.priceRegular?.toPlainString(),
                priceDiscount = tag.priceDiscount?.toPlainString(),
                weightValue = tag.weightValue?.toPlainString(),
                weightUnit = tag.weightUnit?.name,
                barcode = tag.barcode,
                pricePerUnit = price?.pricePerUnit?.toPlainString(),
                pricePerUnitDiscount = price?.pricePerUnitDiscount?.toPlainString(),
                displayUnit = price?.displayUnit?.name
            )
        )
    }

    override suspend fun getProcessingScans(): List<Scan> {
        return scanDao.getProcessingScans()
    }

    override suspend fun createManual(): Long {
        return scanDao.insert(Scan(status = ScanStatus.PROCESSING))
    }

    override suspend fun updateUserFields(
        scanId: Long,
        tag: ParsedPriceTag,
        price: PriceResult?,
        storeName: String?,
        latitude: Double?,
        longitude: Double?
    ) {
        val existing = scanDao.getById(scanId) ?: return
        val newStatus = if (existing.status == ScanStatus.COMPLETED || existing.status == ScanStatus.EDITED) {
            ScanStatus.EDITED
        } else {
            ScanStatus.COMPLETED
        }
        scanDao.update(
            existing.copy(
                status = newStatus,
                productName = tag.productName,
                priceRegular = tag.priceRegular?.toPlainString(),
                priceDiscount = tag.priceDiscount?.toPlainString(),
                weightValue = tag.weightValue?.toPlainString(),
                weightUnit = tag.weightUnit?.name,
                barcode = tag.barcode,
                pricePerUnit = price?.pricePerUnit?.toPlainString(),
                pricePerUnitDiscount = price?.pricePerUnitDiscount?.toPlainString(),
                displayUnit = price?.displayUnit?.name,
                storeName = storeName,
                latitude = latitude,
                longitude = longitude
            )
        )
    }

    override fun getAllScansFlow(): Flow<List<Scan>> {
        return scanDao.getAllScansFlow()
    }

    override suspend fun getById(scanId: Long): Scan? {
        return scanDao.getById(scanId)
    }

    override suspend fun delete(scanId: Long) {
        scanDao.deleteById(scanId)
    }

    override suspend fun getAllScans(): List<Scan> {
        return scanDao.getAllScans()
    }
}
