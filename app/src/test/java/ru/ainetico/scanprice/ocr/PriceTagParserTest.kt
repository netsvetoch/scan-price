package ru.ainetico.scanprice.ocr

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.ainetico.scanprice.model.WeightUnit
import ru.ainetico.scanprice.ocr.PriceTagParser.optStringOrNull
import ru.ainetico.scanprice.ocr.PriceTagParser.toBigDecimalSafe
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class PriceTagParserTest {

    @Test
    fun `parse extracts all fields from complete JSON`() {
        val json = JSONObject("""
            {"product_name":"Молоко","product_description":"3.2% жирности",
             "price_regular":89.90,"price_discount":79.90,
             "weight_value":1,"weight_unit":"л"}
        """.trimIndent())

        val tag = PriceTagParser.parse(json)

        assertEquals("Молоко", tag.productName)
        assertEquals("3.2% жирности", tag.productDescription)
        assertTrue(BigDecimal("89.90").compareTo(tag.priceRegular) == 0)
        assertTrue(BigDecimal("79.90").compareTo(tag.priceDiscount) == 0)
        assertEquals(BigDecimal("1"), tag.weightValue)
        assertEquals(WeightUnit.L, tag.weightUnit)
    }

    @Test
    fun `parse handles all null optional fields`() {
        val json = JSONObject("""
            {"product_name":"Хлеб","product_description":null,
             "price_regular":45,"price_discount":null,
             "weight_value":null,"weight_unit":null}
        """.trimIndent())

        val tag = PriceTagParser.parse(json)

        assertEquals("Хлеб", tag.productName)
        assertNull(tag.productDescription)
        assertEquals(BigDecimal("45"), tag.priceRegular)
        assertNull(tag.priceDiscount)
        assertNull(tag.weightValue)
        assertNull(tag.weightUnit)
    }

    @Test
    fun `parse maps all Russian weight units correctly`() {
        val mappings = listOf(
            "г" to WeightUnit.G,
            "кг" to WeightUnit.KG,
            "мл" to WeightUnit.ML,
            "л" to WeightUnit.L,
            "шт" to WeightUnit.PCS
        )
        for ((unitStr, expected) in mappings) {
            val json = JSONObject().apply {
                put("product_name", "X")
                put("price_regular", 1)
                put("weight_value", 100)
                put("weight_unit", unitStr)
            }
            val tag = PriceTagParser.parse(json)
            assertEquals("Unit '$unitStr' should map to $expected", expected, tag.weightUnit)
        }
    }

    @Test
    fun `parse treats zero discount as null`() {
        val json = JSONObject("""
            {"product_name":"X","price_regular":100,"price_discount":0,
             "weight_value":null,"weight_unit":null}
        """.trimIndent())

        val tag = PriceTagParser.parse(json)

        assertNull(tag.priceDiscount)
    }

    @Test
    fun `parse handles missing optional fields`() {
        val json = JSONObject("""{"product_name":"Сыр","price_regular":200}""")

        val tag = PriceTagParser.parse(json)

        assertEquals("Сыр", tag.productName)
        assertTrue(BigDecimal("200").compareTo(tag.priceRegular) == 0)
        assertNull(tag.productDescription)
        assertNull(tag.priceDiscount)
        assertNull(tag.weightValue)
        assertNull(tag.weightUnit)
    }

    @Test
    fun `parse handles uppercase weight units via lowercase normalization`() {
        val json = JSONObject().apply {
            put("product_name", "X")
            put("price_regular", 1)
            put("weight_unit", "КГ")
        }
        val tag = PriceTagParser.parse(json)
        assertEquals(WeightUnit.KG, tag.weightUnit)
    }

    @Test
    fun `parse returns null unit for unknown weight unit`() {
        val json = JSONObject().apply {
            put("product_name", "X")
            put("price_regular", 1)
            put("weight_unit", "фунт")
        }
        val tag = PriceTagParser.parse(json)
        assertNull(tag.weightUnit)
    }

    @Test
    fun `parse handles empty JSON with no required fields`() {
        val json = JSONObject("{}")
        val tag = PriceTagParser.parse(json)

        assertNull(tag.productName)
        assertNull(tag.priceRegular)
        assertNull(tag.weightUnit)
    }

    // --- optStringOrNull tests ---

    @Test
    fun `optStringOrNull returns null for blank value`() {
        val json = JSONObject("""{"key":""}""")
        assertNull(json.optStringOrNull("key"))
    }

    @Test
    fun `optStringOrNull returns null for literal null string`() {
        val json = JSONObject("""{"key":"null"}""")
        assertNull(json.optStringOrNull("key"))
    }

    @Test
    fun `optStringOrNull returns null for missing key`() {
        val json = JSONObject("{}")
        assertNull(json.optStringOrNull("missing"))
    }

    @Test
    fun `optStringOrNull returns value for present key`() {
        val json = JSONObject("""{"key":"hello"}""")
        assertEquals("hello", json.optStringOrNull("key"))
    }

    // --- toBigDecimalSafe tests ---

    @Test
    fun `toBigDecimalSafe parses normal decimal`() {
        assertTrue(BigDecimal("89.90").compareTo("89.90".toBigDecimalSafe()) == 0)
    }

    @Test
    fun `toBigDecimalSafe handles Russian comma separator`() {
        assertTrue(BigDecimal("89.90").compareTo("89,90".toBigDecimalSafe()) == 0)
    }

    @Test
    fun `toBigDecimalSafe strips non-numeric characters`() {
        assertTrue(BigDecimal("1500").compareTo("1 500 руб".toBigDecimalSafe()) == 0)
    }

    @Test
    fun `toBigDecimalSafe returns null for empty string`() {
        assertNull("".toBigDecimalSafe())
    }

    @Test
    fun `toBigDecimalSafe returns null for non-numeric string`() {
        assertNull("abc".toBigDecimalSafe())
    }

    @Test
    fun `toBigDecimalSafe parses integer`() {
        assertEquals(BigDecimal("100"), "100".toBigDecimalSafe())
    }
}
