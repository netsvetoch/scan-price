package ru.ainetico.scanprice.data

import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class DataExporterTest {

    private val context = RuntimeEnvironment.getApplication()
    private val exporter = DataExporter(context)

    @Test
    fun `export creates CSV with header and data rows`() = runTest {
        val scans = listOf(
            Scan(id = 1, productName = "Молоко", priceRegular = "89.90", weightValue = "1", weightUnit = "L", storeName = "Магнит"),
            Scan(id = 2, productName = "Хлеб", priceRegular = "45", storeName = "Пятёрочка")
        )

        val result = exporter.export(scans)

        assertTrue(result.csvFile.exists())
        val lines = result.csvFile.readLines()
        assertEquals(3, lines.size) // header + 2 rows
        assertTrue(lines[0].startsWith("product_name,"))
        assertTrue(lines[1].contains("Молоко"))
        assertTrue(lines[2].contains("Хлеб"))
    }

    @Test
    fun `export handles empty scan list`() = runTest {
        val result = exporter.export(emptyList())

        assertTrue(result.csvFile.exists())
        val lines = result.csvFile.readLines()
        assertEquals(1, lines.size) // header only
        assertNull(result.zipFile)
    }

    @Test
    fun `export escapes commas in product name`() = runTest {
        val scans = listOf(
            Scan(id = 1, productName = "Сыр, нарезка", priceRegular = "200")
        )

        val result = exporter.export(scans)
        val dataLine = result.csvFile.readLines()[1]

        assertTrue("Comma in name should be quoted", dataLine.contains("\"Сыр, нарезка\""))
    }

    @Test
    fun `export escapes quotes in product name`() = runTest {
        val scans = listOf(
            Scan(id = 1, productName = "Сыр \"Российский\"", priceRegular = "300")
        )

        val result = exporter.export(scans)
        val dataLine = result.csvFile.readLines()[1]

        assertTrue("Quotes should be doubled", dataLine.contains("\"\"Российский\"\""))
    }

    @Test
    fun `export prevents CSV injection with formula characters`() = runTest {
        val scans = listOf(
            Scan(id = 1, productName = "=cmd|'/c calc'!A1"),
            Scan(id = 2, productName = "+malicious"),
            Scan(id = 3, productName = "@evil"),
            Scan(id = 4, productName = "-bad")
        )

        val result = exporter.export(scans)
        val lines = result.csvFile.readLines()

        for (i in 1..4) {
            val line = lines[i]
            assertFalse(
                "Line $i should not start field with formula char: $line",
                line.matches(Regex("^[=+@-].*"))
            )
        }
    }

    @Test
    fun `export handles null fields gracefully`() = runTest {
        val scans = listOf(
            Scan(id = 1) // all fields null except defaults
        )

        val result = exporter.export(scans)
        val lines = result.csvFile.readLines()

        assertEquals(2, lines.size)
        // Should not throw, and row should have correct number of commas
        val commaCount = lines[1].count { it == ',' }
        assertEquals("Should have 10 commas for 11 fields", 10, commaCount)
    }

    @Test
    fun `export returns null zipFile when no images`() = runTest {
        val scans = listOf(
            Scan(id = 1, productName = "Test", imagePath = null)
        )

        val result = exporter.export(scans)
        assertNull(result.zipFile)
    }
}
