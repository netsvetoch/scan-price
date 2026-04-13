package ru.ainetico.scanprice.ocr

import org.json.JSONObject
import ru.ainetico.scanprice.model.ParsedPriceTag
import ru.ainetico.scanprice.model.WeightUnit
import java.math.BigDecimal

/**
 * Shared JSON → ParsedPriceTag parsing logic used by both
 * LocalVisionEngine and RemoteVisionClient.
 */
object PriceTagParser {

  fun parse(json: JSONObject): ParsedPriceTag {
    val unit = json.optStringOrNull("weight_unit")?.lowercase()?.let { raw ->
      when {
        raw.contains("кг") -> WeightUnit.KG
        raw.contains("г") -> WeightUnit.G
        raw.contains("мл") -> WeightUnit.ML
        raw.contains("л") -> WeightUnit.L
        raw.contains("шт") -> WeightUnit.PCS
        else -> null
      }
    }

    val discount = json.optStringOrNull("price_discount")?.toBigDecimalSafe()

    return ParsedPriceTag(
      productName = json.optStringOrNull("product_name"),
      productDescription = json.optStringOrNull("product_description"),
      priceRegular = json.optStringOrNull("price_regular")?.toBigDecimalSafe(),
      priceDiscount = if (discount != null && discount.compareTo(BigDecimal.ZERO) == 0) null else discount,
      weightValue = json.optStringOrNull("weight_value")?.toBigDecimalSafe(),
      weightUnit = unit
    )
  }

  fun JSONObject.optStringOrNull(key: String): String? {
    val value = optString(key, "")
    return if (value.isBlank() || value == "null") null else value
  }

  fun String.toBigDecimalSafe(): BigDecimal? {
    return try {
      val cleaned = this.replace(Regex("""[^\d.,]"""), "").replace(',', '.')
      if (cleaned.isBlank()) null else BigDecimal(cleaned)
    } catch (_: Exception) {
      null
    }
  }
}
