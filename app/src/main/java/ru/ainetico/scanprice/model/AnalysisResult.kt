package ru.ainetico.scanprice.model

data class AnalysisResult(
  val tag: ParsedPriceTag,
  val price: PriceResult?
)
