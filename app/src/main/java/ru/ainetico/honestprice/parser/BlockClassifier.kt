package ru.ainetico.honestprice.parser

import ru.ainetico.honestprice.model.OcrBlock

class BlockClassifier {

    companion object {
        // Strict: price with decimal separator (89.90, 269,99)
        private val PRICE_WITH_DECIMAL = Regex("""\d+[.,]\d{2}""")
        // Relaxed: pure digits that could be a price (26999 → 269.99, 2999 → 29.99)
        private val PRICE_DIGITS_ONLY = Regex("""^\d{3,6}$""")
        // Any number (for bounding box based classification)
        private val NUMBER_PATTERN = Regex("""\d+""")

        private val PRICE_CONTEXT = Regex("""[₽]|руб|р\.|р$""", RegexOption.IGNORE_CASE)
        private val DISCOUNT_CONTEXT = Regex(
            """карт|скидк|цена для вас|по карте|выгод""",
            RegexOption.IGNORE_CASE
        )
        private val DISCOUNT_LABEL_ONLY = Regex(
            """^(с картой|по карте|скидка|выгода)$""",
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

        // Date check BEFORE price
        if (DATE_PATTERN.containsMatchIn(text) && !PRICE_CONTEXT.containsMatchIn(text)) {
            return BlockRole.NOISE
        }

        // "С КАРТОЙ" / "ПО КАРТЕ" without a price number = discount label
        if (DISCOUNT_LABEL_ONLY.containsMatchIn(text)) {
            return BlockRole.DISCOUNT_PRICE
        }

        // Discount context + price pattern
        if (DISCOUNT_CONTEXT.containsMatchIn(text) && hasPriceNumber(text)) {
            return BlockRole.DISCOUNT_PRICE
        }

        // Price with decimal separator + context or large box
        if (PRICE_WITH_DECIMAL.containsMatchIn(text) &&
            (PRICE_CONTEXT.containsMatchIn(text) || hasLargeBoundingBox(block, imageHeight))
        ) {
            return BlockRole.PRICE
        }

        // Pure digits with large bounding box = likely a price (e.g. "26999", "2999", "29")
        val cleanText = text.replace(Regex("""[^0-9]"""), "")
        if (cleanText.isNotEmpty() && NUMBER_PATTERN.matches(cleanText) &&
            hasLargeBoundingBox(block, imageHeight) &&
            block.boundingBox.height() > imageHeight * 0.1
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

    private fun hasPriceNumber(text: String): Boolean {
        return PRICE_WITH_DECIMAL.containsMatchIn(text) || PRICE_DIGITS_ONLY.containsMatchIn(text)
    }

    private fun hasLargeBoundingBox(block: OcrBlock, imageHeight: Int): Boolean {
        val boxHeight = block.boundingBox.height()
        return boxHeight > imageHeight * 0.05
    }

    private fun isInUpperThird(block: OcrBlock, imageHeight: Int): Boolean {
        return block.boundingBox.top < imageHeight / 3
    }
}
