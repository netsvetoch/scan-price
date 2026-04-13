package ru.ainetico.honestprice.model

data class AnalysisResult(
  val tag: ParsedPriceTag,
  val price: PriceResult?
)
