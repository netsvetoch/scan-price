package ru.ainetico.scanprice.calculator

import ru.ainetico.scanprice.model.ParsedPriceTag
import ru.ainetico.scanprice.model.PriceResult
import ru.ainetico.scanprice.model.WeightUnit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class PriceCalculator {

  companion object {
    private val MATH_CONTEXT = MathContext(10, RoundingMode.HALF_UP)

    private val CONVERSION_TO_BASE: Map<WeightUnit, BigDecimal> = mapOf(
      WeightUnit.G to BigDecimal("0.001"),
      WeightUnit.KG to BigDecimal.ONE,
      WeightUnit.ML to BigDecimal("0.001"),
      WeightUnit.L to BigDecimal.ONE,
      WeightUnit.PCS to BigDecimal.ONE
    )

    private val CONVERSION_FROM_BASE: Map<WeightUnit, BigDecimal> = mapOf(
      WeightUnit.G to BigDecimal("1000"),
      WeightUnit.KG to BigDecimal.ONE,
      WeightUnit.ML to BigDecimal("1000"),
      WeightUnit.L to BigDecimal.ONE,
      WeightUnit.PCS to BigDecimal.ONE
    )
  }

  fun calculate(tag: ParsedPriceTag, targetUnit: WeightUnit? = null): PriceResult? {
    val priceRegular = tag.priceRegular ?: return null
    if (priceRegular <= BigDecimal.ZERO) return null

    val weightUnit = tag.weightUnit ?: WeightUnit.PCS
    val weightValue = tag.weightValue ?: BigDecimal.ONE
    if (weightValue <= BigDecimal.ZERO) return null

    // Ignore discount if it's negative or exceeds regular price
    val validDiscount = tag.priceDiscount?.takeIf {
      it > BigDecimal.ZERO && it < priceRegular
    }

    val baseUnit = weightUnit.baseUnit ?: weightUnit
    if (baseUnit == WeightUnit.PCS) {
      return PriceResult(
        pricePerUnit = priceRegular,
        pricePerUnitDiscount = validDiscount,
        displayUnit = WeightUnit.PCS,
        source = tag
      )
    }

    val displayUnit = targetUnit ?: baseUnit
    // Guard against cross-group conversion (kg↔l is impossible)
    val targetBase = displayUnit.baseUnit ?: displayUnit
    val safeDisplayUnit = if (targetBase == baseUnit) displayUnit else baseUnit
    val weightInBase = weightValue.multiply(CONVERSION_TO_BASE[weightUnit]!!, MATH_CONTEXT)
    val displayFactor = CONVERSION_FROM_BASE[safeDisplayUnit] ?: BigDecimal.ONE
    val effectiveWeight = weightInBase.multiply(displayFactor, MATH_CONTEXT)

    val pricePerUnit = priceRegular.divide(effectiveWeight, MATH_CONTEXT)
    val pricePerUnitDiscount = validDiscount?.divide(effectiveWeight, MATH_CONTEXT)

    return PriceResult(
      pricePerUnit = pricePerUnit,
      pricePerUnitDiscount = pricePerUnitDiscount,
      displayUnit = safeDisplayUnit,
      source = tag
    )
  }
}
