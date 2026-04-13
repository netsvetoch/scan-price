package ru.ainetico.scanprice.ocr

import io.mockk.mockk
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.ainetico.scanprice.data.AppSettings
import ru.ainetico.scanprice.model.WeightUnit
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class RemoteVisionClientTest {

    private val client = RemoteVisionClient(mockk<AppSettings>())

    @Test
    fun `parseResponse extracts all fields from valid JSON`() {
        val json = """{"product_name":"Молоко","product_description":"3.2% жирности","price_regular":89.90,"price_discount":79.90,"weight_value":1,"weight_unit":"л"}"""
        val tag = client.parseResponse(json)

        assertEquals("Молоко", tag.productName)
        assertEquals("3.2% жирности", tag.productDescription)
        assertTrue(BigDecimal("89.90").compareTo(tag.priceRegular) == 0)
        assertTrue(BigDecimal("79.90").compareTo(tag.priceDiscount) == 0)
        assertEquals(BigDecimal("1"), tag.weightValue)
        assertEquals(WeightUnit.L, tag.weightUnit)
    }

    @Test
    fun `parseResponse handles null fields`() {
        val json = """{"product_name":"Хлеб","product_description":null,"price_regular":45,"price_discount":null,"weight_value":null,"weight_unit":null}"""
        val tag = client.parseResponse(json)

        assertEquals("Хлеб", tag.productName)
        assertNull(tag.productDescription)
        assertEquals(BigDecimal("45"), tag.priceRegular)
        assertNull(tag.priceDiscount)
        assertNull(tag.weightValue)
        assertNull(tag.weightUnit)
    }

    @Test
    fun `parseResponse maps all Russian weight units`() {
        for ((unitStr, expected) in listOf("г" to WeightUnit.G, "кг" to WeightUnit.KG, "мл" to WeightUnit.ML, "л" to WeightUnit.L, "шт" to WeightUnit.PCS)) {
            val json = """{"product_name":"X","product_description":null,"price_regular":1,"price_discount":null,"weight_value":100,"weight_unit":"$unitStr"}"""
            val tag = client.parseResponse(json)
            assertEquals("Unit '$unitStr' should map to $expected", expected, tag.weightUnit)
        }
    }

    @Test
    fun `parseResponse treats zero discount as null`() {
        val json = """{"product_name":"X","product_description":null,"price_regular":100,"price_discount":0,"weight_value":null,"weight_unit":null}"""
        val tag = client.parseResponse(json)

        assertNull(tag.priceDiscount)
    }

    @Test
    fun `parseResponse strips markdown code fences`() {
        val json = "```json\n{\"product_name\":\"Сок\",\"product_description\":null,\"price_regular\":120,\"price_discount\":null,\"weight_value\":1,\"weight_unit\":\"л\"}\n```"
        val tag = client.parseResponse(json)

        assertEquals("Сок", tag.productName)
        assertEquals(BigDecimal("120"), tag.priceRegular)
    }

    @Test
    fun `parseResponse extracts JSON from surrounding text`() {
        val content = "Here is the result: {\"product_name\":\"Масло\",\"product_description\":null,\"price_regular\":250,\"price_discount\":null,\"weight_value\":180,\"weight_unit\":\"г\"} done"
        val tag = client.parseResponse(content)

        assertEquals("Масло", tag.productName)
        assertEquals(BigDecimal("250"), tag.priceRegular)
        assertEquals(WeightUnit.G, tag.weightUnit)
    }

    @Test
    fun `parseResponse handles Russian comma decimal separator`() {
        val json = """{"product_name":"X","product_description":null,"price_regular":"89,90","price_discount":null,"weight_value":null,"weight_unit":null}"""
        val tag = client.parseResponse(json)

        assertTrue(BigDecimal("89.90").compareTo(tag.priceRegular) == 0)
    }
}
