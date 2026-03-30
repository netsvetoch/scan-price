package ru.ainetico.honestprice.data

import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.PriceResult

interface ScanRepository {
    suspend fun createProcessing(imagePath: String): Long
    suspend fun markCompleted(scanId: Long, tag: ParsedPriceTag, price: PriceResult?)
    suspend fun getProcessingScans(): List<Scan>
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
}
