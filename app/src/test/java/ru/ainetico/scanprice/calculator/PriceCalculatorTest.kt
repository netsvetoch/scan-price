package ru.ainetico.scanprice.calculator

import ru.ainetico.scanprice.model.ParsedPriceTag
import ru.ainetico.scanprice.model.WeightUnit
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.math.BigDecimal
import java.math.RoundingMode

class PriceCalculatorTest {

    private val calculator = PriceCalculator()

    @ParameterizedTest(name = "price={0} weight={1} {2} → {3} per {4}")
    @CsvSource(
        "50.00,  500,  G,   100.00, KG",
        "60.00,  330,  ML,  181.82, L",
        "25.00,  6,    PCS, 25.00,  PCS",
        "250.00, 1.5,  KG,  166.67, KG"
    )
    fun `calculates price per unit`(
        price: String, weight: String, unit: String,
        expectedPrice: String, expectedUnit: String
    ) {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal(price),
            weightValue = BigDecimal(weight),
            weightUnit = WeightUnit.valueOf(unit)
        )
        val result = calculator.calculate(tag)!!
        assertEquals(BigDecimal(expectedPrice), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.valueOf(expectedUnit), result.displayUnit)
    }

    @ParameterizedTest(name = "price={0} weight={1} {2} → null")
    @CsvSource(
        nullValues = ["NULL"],
        value = [
            "NULL,   500,  G",
            "-50.00, 500,  G",
            "0,      500,  G",
            "100.00, -500, G",
            "100.00, 0,    G"
        ]
    )
    fun `returns null for invalid inputs`(price: String?, weight: String?, unit: String) {
        val tag = ParsedPriceTag(
            priceRegular = price?.let { BigDecimal(it) },
            weightValue = weight?.let { BigDecimal(it) },
            weightUnit = WeightUnit.valueOf(unit)
        )
        assertNull(calculator.calculate(tag))
    }

    @ParameterizedTest(name = "regular={0} discount={1} weight={2} {3} → discount ignored")
    @CsvSource(
        nullValues = ["NULL"],
        value = [
            "100.00, 150.00, 500,  G",
            "100.00, 100.00, 500,  G",
            "100.00, -10.00, 500,  G",
            "25.00,  30.00,  NULL, PCS"
        ]
    )
    fun `ignores invalid discount`(
        price: String, discount: String, weight: String?, unit: String
    ) {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal(price),
            priceDiscount = BigDecimal(discount),
            weightValue = weight?.let { BigDecimal(it) },
            weightUnit = WeightUnit.valueOf(unit)
        )
        val result = calculator.calculate(tag)!!
        assertNull(result.pricePerUnitDiscount)
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
