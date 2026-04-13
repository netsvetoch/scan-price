package ru.ainetico.honestprice.model

enum class WeightUnit(val displayName: String, private val baseUnitName: String?) {
  KG("кг", null),
  G("г", "KG"),
  L("л", null),
  ML("мл", "L"),
  PCS("шт", null);

  val baseUnit: WeightUnit? get() = baseUnitName?.let { valueOf(it) }
}
