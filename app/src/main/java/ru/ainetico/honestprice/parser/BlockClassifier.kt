package ru.ainetico.honestprice.parser

import ru.ainetico.honestprice.model.OcrBlock

class BlockClassifier {

    companion object {
        private val PRICE_PATTERN = Regex("""\d+[.,]\d{2}""")
        private val PRICE_CONTEXT = Regex("""[₽]|руб|р\.""", RegexOption.IGNORE_CASE)
        private val DISCOUNT_CONTEXT = Regex(
            """карт|скидк|цена для вас|по карте|выгод""",
            RegexOption.IGNORE_CASE
        )
        private val WEIGHT_PATTERN = Regex(
            """\d+[.,]?\d*\s*(г|гр|кг|мл|л|шт|уп)\.?""",
            RegexOption.IGNORE_CASE
        )
        private val DATE_PATTERN = Regex("""\d{2}\.\d{2}\.\d{2,4}""")
    }

    fun classify(block: OcrBlock, imageHeight: Int): BlockRole {
        val text = block.text.lowercase().trim()

        // Date check BEFORE price — dates like "01.03.2026" match PRICE_PATTERN
        if (DATE_PATTERN.containsMatchIn(text) && !PRICE_CONTEXT.containsMatchIn(text)) {
            return BlockRole.NOISE
        }

        if (DISCOUNT_CONTEXT.containsMatchIn(text) && PRICE_PATTERN.containsMatchIn(text)) {
            return BlockRole.DISCOUNT_PRICE
        }

        if (PRICE_PATTERN.containsMatchIn(text) &&
            (PRICE_CONTEXT.containsMatchIn(text) || hasLargeBoundingBox(block, imageHeight))
        ) {
            return BlockRole.PRICE
        }

        if (WEIGHT_PATTERN.containsMatchIn(text)) {
            return BlockRole.WEIGHT
        }

        if (isInUpperThird(block, imageHeight) && text.length > 2) {
            return BlockRole.NAME
        }

        return BlockRole.NOISE
    }

    private fun hasLargeBoundingBox(block: OcrBlock, imageHeight: Int): Boolean {
        val boxHeight = block.boundingBox.height()
        return boxHeight > imageHeight * 0.05
    }

    private fun isInUpperThird(block: OcrBlock, imageHeight: Int): Boolean {
        return block.boundingBox.top < imageHeight / 3
    }
}
