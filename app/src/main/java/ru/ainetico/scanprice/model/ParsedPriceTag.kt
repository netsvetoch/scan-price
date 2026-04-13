package ru.ainetico.honestprice.model

import java.math.BigDecimal

data class ParsedPriceTag(
  val productName: String? = null,
  val productDescription: String? = null,
  val priceRegular: BigDecimal? = null,
  val priceDiscount: BigDecimal? = null,
  val weightValue: BigDecimal? = null,
  val weightUnit: WeightUnit? = null,
  val barcode: String? = null
)
