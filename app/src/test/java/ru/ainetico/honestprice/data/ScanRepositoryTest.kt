package ru.ainetico.honestprice.data

import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.PriceResult
import ru.ainetico.honestprice.model.WeightUnit
import java.math.BigDecimal

class ScanRepositoryTest {

    private val dao = mockk<ScanDao>(relaxed = true)
    private val repo: ScanRepository = ScanRepositoryImpl(dao)

    @Test
    fun `createProcessing inserts scan with image path`() = runTest {
        coEvery { dao.insert(any()) } returns 42L

        val id = repo.createProcessing("/path/to/image.jpg")

        assertEquals(42L, id)
        coVerify { dao.insert(match { it.imagePath == "/path/to/image.jpg" }) }
    }

    @Test
    fun `markCompleted updates scan with parsed tag data`() = runTest {
        val existing = Scan(id = 1, status = ScanStatus.PROCESSING)
        coEvery { dao.getById(1) } returns existing

        val tag = ParsedPriceTag(
            productName = "Молоко",
            priceRegular = BigDecimal("89.90"),
            weightValue = BigDecimal("1"),
            weightUnit = WeightUnit.L
        )
        val price = PriceResult(
            pricePerUnit = BigDecimal("89.90"),
            pricePerUnitDiscount = null,
            displayUnit = WeightUnit.L,
            source = tag
        )

        repo.markCompleted(1, tag, price)

        val scanSlot = slot<Scan>()
        coVerify { dao.update(capture(scanSlot)) }

        val updated = scanSlot.captured
        assertEquals(ScanStatus.COMPLETED, updated.status)
        assertEquals("Молоко", updated.productName)
        assertEquals("89.90", updated.priceRegular)
        assertEquals("1", updated.weightValue)
        assertEquals("L", updated.weightUnit)
        assertEquals("89.90", updated.pricePerUnit)
        assertEquals("L", updated.displayUnit)
    }

    @Test
    fun `markCompleted does nothing when scan not found`() = runTest {
        coEvery { dao.getById(999) } returns null

        repo.markCompleted(999, ParsedPriceTag(), null)

        coVerify(exactly = 0) { dao.update(any()) }
    }

    @Test
    fun `updateUserFields sets EDITED status for completed scan`() = runTest {
        val existing = Scan(id = 1, status = ScanStatus.COMPLETED)
        coEvery { dao.getById(1) } returns existing

        repo.updateUserFields(
            scanId = 1,
            tag = ParsedPriceTag(productName = "Хлеб", priceRegular = BigDecimal("45")),
            price = null,
            storeName = "Пятёрочка",
            latitude = 55.75,
            longitude = 37.62
        )

        val scanSlot = slot<Scan>()
        coVerify { dao.update(capture(scanSlot)) }

        val updated = scanSlot.captured
        assertEquals(ScanStatus.EDITED, updated.status)
        assertEquals("Хлеб", updated.productName)
        assertEquals("Пятёрочка", updated.storeName)
        assertEquals(55.75, updated.latitude!!, 0.001)
    }

    @Test
    fun `updateUserFields sets COMPLETED status for processing scan`() = runTest {
        val existing = Scan(id = 1, status = ScanStatus.PROCESSING)
        coEvery { dao.getById(1) } returns existing

        repo.updateUserFields(
            scanId = 1,
            tag = ParsedPriceTag(productName = "Сок"),
            price = null,
            storeName = null,
            latitude = null,
            longitude = null
        )

        val scanSlot = slot<Scan>()
        coVerify { dao.update(capture(scanSlot)) }
        assertEquals(ScanStatus.COMPLETED, scanSlot.captured.status)
    }

    @Test
    fun `delete calls dao deleteById`() = runTest {
        repo.delete(5)
        coVerify { dao.deleteById(5) }
    }

    @Test
    fun `getById delegates to dao`() = runTest {
        val scan = Scan(id = 3, productName = "Тест")
        coEvery { dao.getById(3) } returns scan

        val result = repo.getById(3)

        assertEquals(scan, result)
    }
}
