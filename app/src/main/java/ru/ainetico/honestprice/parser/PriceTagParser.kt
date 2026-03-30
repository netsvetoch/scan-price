package ru.ainetico.honestprice.parser

import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import java.math.BigDecimal

class PriceTagParser {

    private val classifier = BlockClassifier()

    companion object {
        private val PRICE_VALUE = Regex("""\d+[.,]\d{2}""")
        private val WEIGHT_VALUE = Regex("""\d+[.,]?\d*""")
        private val UNIT_PATTERN = Regex(
            """(кг|г|гр|мл|л|шт|уп)\.?""",
            RegexOption.IGNORE_CASE
        )
    }

    fun parse(ocrResult: OcrResult, barcode: String?): ParsedPriceTag {
        if (ocrResult.blocks.isEmpty()) {
            return ParsedPriceTag(barcode = barcode)
        }

        val imageHeight = ocrResult.blocks.maxOf { it.boundingBox.bottom }
        val classified = ocrResult.blocks.map { block ->
            classifier.classify(block, imageHeight) to block
        }

        val name = extractName(classified)
        val (priceRegular, priceDiscount) = extractPrices(classified)
        val (weightValue, weightUnit) = extractWeight(classified, classified.filter {
            it.first == BlockRole.PRICE || it.first == BlockRole.DISCOUNT_PRICE
        })

        return ParsedPriceTag(
            productName = name,
            priceRegular = priceRegular,
            priceDiscount = priceDiscount,
            weightValue = weightValue,
            weightUnit = weightUnit,
            barcode = barcode,
            rawBlocks = ocrResult.blocks
        )
    }

    private fun extractName(classified: List<Pair<BlockRole, OcrBlock>>): String? {
        return classified
            .filter { it.first == BlockRole.NAME }
            .maxByOrNull { it.second.boundingBox.width() * it.second.boundingBox.height() }
            ?.second?.text?.trim()
    }

    private fun extractPrices(
        classified: List<Pair<BlockRole, OcrBlock>>
    ): Pair<BigDecimal?, BigDecimal?> {
        val discountBlocks = classified.filter { it.first == BlockRole.DISCOUNT_PRICE }
        val priceBlocks = classified.filter { it.first == BlockRole.PRICE }

        // Handle two+ PRICE blocks with no DISCOUNT classification — smaller is discount
        if (priceBlocks.size >= 2 && discountBlocks.isEmpty()) {
            val prices = priceBlocks.mapNotNull { extractPriceValue(it.second.text) }.sortedDescending()
            return if (prices.size >= 2) prices[0] to prices[1] else prices.firstOrNull() to null
        }

        val discountPrice = discountBlocks.firstOrNull()?.let { extractPriceValue(it.second.text) }
        val regularPrice = priceBlocks.firstOrNull()?.let { extractPriceValue(it.second.text) }

        if (regularPrice != null && discountPrice != null) {
            return if (regularPrice >= discountPrice) {
                regularPrice to discountPrice
            } else {
                discountPrice to regularPrice
            }
        }

        if (regularPrice != null) return regularPrice to null
        if (discountPrice != null) return discountPrice to null

        return null to null
    }

    private fun extractPriceValue(text: String): BigDecimal? {
        return PRICE_VALUE.find(text)?.value
            ?.replace(',', '.')
            ?.let { BigDecimal(it) }
    }

    private fun extractWeight(
        classified: List<Pair<BlockRole, OcrBlock>>,
        priceBlocks: List<Pair<BlockRole, OcrBlock>>
    ): Pair<BigDecimal?, WeightUnit?> {
        val weightBlocks = classified.filter { it.first == BlockRole.WEIGHT }
        if (weightBlocks.isEmpty()) return null to null

        val block = if (weightBlocks.size == 1 || priceBlocks.isEmpty()) {
            weightBlocks.first()
        } else {
            val priceCenter = priceBlocks.first().second.boundingBox.centerY()
            weightBlocks.minBy {
                kotlin.math.abs(it.second.boundingBox.centerY() - priceCenter)
            }
        }

        val text = block.second.text.lowercase().trim()
        val value = WEIGHT_VALUE.find(text)?.value?.replace(',', '.')?.let { BigDecimal(it) }
        val unit = UNIT_PATTERN.find(text)?.groupValues?.get(1)?.let { parseUnit(it) }

        return value to unit
    }

    private fun parseUnit(raw: String): WeightUnit {
        return when (raw.lowercase().trimEnd('.')) {
            "кг" -> WeightUnit.KG
            "г", "гр" -> WeightUnit.G
            "л" -> WeightUnit.L
            "мл" -> WeightUnit.ML
            "шт", "уп" -> WeightUnit.PCS
            else -> WeightUnit.PCS
        }
    }
}
