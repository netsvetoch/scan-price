package ru.ainetico.scanprice.model

import java.math.BigDecimal

data class PriceResult(
  val pricePerUnit: BigDecimal,
  val pricePerUnitDiscount: BigDecimal?,
  val displayUnit: WeightUnit,
  val source: ParsedPriceTag
)
