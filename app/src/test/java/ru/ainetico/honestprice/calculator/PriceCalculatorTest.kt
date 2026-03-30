package ru.ainetico.honestprice.calculator

import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class PriceCalculatorTest {

    private val calculator = PriceCalculator()

    @Test
    fun `calculates price per kg from grams`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("50.00"),
            weightValue = BigDecimal("500"),
            weightUnit = WeightUnit.G
        )
        val result = calculator.calculate(tag)!!
        assertEquals(BigDecimal("100.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.KG, result.displayUnit)
    }

    @Test
    fun `calculates price per liter from ml`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("60.00"),
            weightValue = BigDecimal("330"),
            weightUnit = WeightUnit.ML
        )
        val result = calculator.calculate(tag)!!
        assertEquals(BigDecimal("181.82"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.L, result.displayUnit)
    }

    @Test
    fun `returns price as-is for PCS`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("25.00"),
            weightValue = BigDecimal("6"),
            weightUnit = WeightUnit.PCS
        )
        val result = calculator.calculate(tag)!!
        assertEquals(BigDecimal("25.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.PCS, result.displayUnit)
    }

    @Test
    fun `returns null when no price`() {
        val tag = ParsedPriceTag(
            weightValue = BigDecimal("500"),
            weightUnit = WeightUnit.G
        )
        assertNull(calculator.calculate(tag))
    }

    @Test
    fun `handles no weight - treats as PCS`() {
        val tag = ParsedPriceTag(priceRegular = BigDecimal("99.00"))
        val result = calculator.calculate(tag)!!
        assertEquals(BigDecimal("99.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.PCS, result.displayUnit)
    }

    @Test
    fun `calculates discount price per unit`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("100.00"),
            priceDiscount = BigDecimal("80.00"),
            weightValue = BigDecimal("500"),
            weightUnit = WeightUnit.G
        )
        val result = calculator.calculate(tag)!!
        assertEquals(BigDecimal("200.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(BigDecimal("160.00"), result.pricePerUnitDiscount!!.setScale(2, RoundingMode.HALF_UP))
    }

    @Test
    fun `calculates price per kg from kg`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("250.00"),
            weightValue = BigDecimal("1.5"),
            weightUnit = WeightUnit.KG
        )
        val result = calculator.calculate(tag)!!
        assertEquals(BigDecimal("166.67"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.KG, result.displayUnit)
    }

    @Test
    fun `custom target unit`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("100.00"),
            weightValue = BigDecimal("1"),
            weightUnit = WeightUnit.KG
        )
        val result = calculator.calculate(tag, targetUnit = WeightUnit.G)!!
        assertEquals(BigDecimal("0.10"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.G, result.displayUnit)
    }
}
