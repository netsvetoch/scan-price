# OCR + Парсер: Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implement the OCR engine, price tag parser, and price calculator subsystem for the ЧестнаяЦена Android app — all on-device, offline-first.

**Architecture:** Three-layer pipeline: ImagePreprocessor → OcrEngine + BarcodeEngine (parallel) → PriceTagParser → PriceCalculator. Orchestrated by ImageAnalyzer. Persistence via ScanRepository interface. All heavy work on background threads via Kotlin Coroutines.

**Tech Stack:** Kotlin, ML Kit Text Recognition, ML Kit Barcode Scanning, CameraX + Camera2 Interop, Room, Kotlin Coroutines, JUnit 4 + Robolectric for unit tests.

**Spec:** `docs/superpowers/specs/2026-03-30-ocr-parser-design.md`

**Base package:** `ru.ainetico.honestprice`

---

## File Structure

```
app/src/main/java/ru/ainetico/honestprice/
├── model/
│   ├── WeightUnit.kt              — enum: KG, G, L, ML, PCS with base unit lookup
│   ├── OcrBlock.kt                — data class: text + boundingBox + confidence
│   ├── OcrResult.kt               — data class: List<OcrBlock>
│   ├── ParsedPriceTag.kt          — data class: all parsed fields, all nullable
│   ├── PriceResult.kt             — data class: pricePerUnit + displayUnit
│   └── AnalysisResult.kt          — data class: tag + price
├── ocr/
│   ├── OcrEngine.kt               — ML Kit Text Recognition wrapper
│   └── BarcodeEngine.kt           — ML Kit Barcode Scanning wrapper
├── parser/
│   ├── BlockRole.kt               — enum: PRICE, DISCOUNT_PRICE, WEIGHT, NAME, NOISE
│   ├── BlockClassifier.kt         — classifies OcrBlocks by role using regex + heuristics
│   └── PriceTagParser.kt          — orchestrates classification + conflict resolution
├── calculator/
│   └── PriceCalculator.kt         — normalizes units, computes price per unit
├── analyzer/
│   └── ImageAnalyzer.kt           — orchestrator: preprocessor → OCR+barcode → parser → calculator
├── image/
│   └── ImagePreprocessor.kt       — crop, EXIF rotation, downsampling
├── data/
│   ├── ScanStatus.kt              — enum: PROCESSING, COMPLETED, EDITED
│   ├── SyncStatus.kt              — enum: LOCAL_ONLY (MVP)
│   ├── Scan.kt                    — Room entity
│   ├── Store.kt                   — Room entity
│   ├── ScanDao.kt                 — Room DAO interface
│   ├── StoreDao.kt                — Room DAO interface
│   ├── Converters.kt              — Room TypeConverters for enums
│   ├── AppDatabase.kt             — Room database
│   └── ScanRepository.kt          — interface + impl wrapping DAO
├── location/
│   └── LocationProvider.kt        — FusedLocationProviderClient wrapper

app/src/test/java/ru/ainetico/honestprice/
├── model/
│   └── WeightUnitTest.kt
├── parser/
│   ├── BlockClassifierTest.kt
│   └── PriceTagParserTest.kt
├── calculator/
│   └── PriceCalculatorTest.kt
├── analyzer/
│   └── ImageAnalyzerTest.kt
├── image/
│   └── ImagePreprocessorTest.kt
```

---

## Task 1: Add dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `app/build.gradle.kts`

- [ ] **Step 1: Add version entries to libs.versions.toml**

Add to `[versions]`:

```toml
mlkitTextRecognition = "16.0.1"
mlkitBarcodeScanning = "17.3.0"
camerax = "1.4.1"
room = "2.6.1"
ksp = "2.2.10-1.0.31"  # IMPORTANT: verify actual KSP version matching project's Kotlin version before use.
                        # Run: ./gradlew --version to check Kotlin version, then find matching KSP at github.com/google/ksp/releases
                        # If Kotlin is 2.2.10, use the latest KSP for that Kotlin version.
playServicesLocation = "21.3.0"
coroutinesTest = "1.9.0"
mockk = "1.13.13"
robolectric = "4.14.1"
```

Add to `[libraries]`:

```toml
mlkit-text-recognition = { group = "com.google.mlkit", name = "text-recognition", version.ref = "mlkitTextRecognition" }
mlkit-barcode-scanning = { group = "com.google.mlkit", name = "barcode-scanning", version.ref = "mlkitBarcodeScanning" }
camerax-core = { group = "androidx.camera", name = "camera-core", version.ref = "camerax" }
camerax-camera2 = { group = "androidx.camera", name = "camera-camera2", version.ref = "camerax" }
camerax-lifecycle = { group = "androidx.camera", name = "camera-lifecycle", version.ref = "camerax" }
camerax-view = { group = "androidx.camera", name = "camera-view", version.ref = "camerax" }
room-runtime = { group = "androidx.room", name = "room-runtime", version.ref = "room" }
room-ktx = { group = "androidx.room", name = "room-ktx", version.ref = "room" }
room-compiler = { group = "androidx.room", name = "room-compiler", version.ref = "room" }
play-services-location = { group = "com.google.android.gms", name = "play-services-location", version.ref = "playServicesLocation" }
kotlinx-coroutines-test = { group = "org.jetbrains.kotlinx", name = "kotlinx-coroutines-test", version.ref = "coroutinesTest" }
mockk = { group = "io.mockk", name = "mockk", version.ref = "mockk" }
robolectric = { group = "org.robolectric", name = "robolectric", version.ref = "robolectric" }
```

Add to `[plugins]`:

```toml
ksp = { id = "com.google.devtools.ksp", version.ref = "ksp" }
```

- [ ] **Step 2: Update app/build.gradle.kts**

Add KSP plugin:

```kotlin
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}
```

Add dependencies:

```kotlin
dependencies {
    // ... existing dependencies ...

    // ML Kit
    implementation(libs.mlkit.text.recognition)
    implementation(libs.mlkit.barcode.scanning)

    // CameraX
    implementation(libs.camerax.core)
    implementation(libs.camerax.camera2)
    implementation(libs.camerax.lifecycle)
    implementation(libs.camerax.view)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Location
    implementation(libs.play.services.location)

    // Testing
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.mockk)
    testImplementation(libs.robolectric)
}
```

- [ ] **Step 3: Sync and verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add gradle/libs.versions.toml app/build.gradle.kts
git commit -m "feat: add ML Kit, CameraX, Room, and test dependencies"
```

---

## Task 2: Model classes

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/model/WeightUnit.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/model/OcrBlock.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/model/OcrResult.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/model/ParsedPriceTag.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/model/PriceResult.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/model/AnalysisResult.kt`
- Test: `app/src/test/java/ru/ainetico/honestprice/model/WeightUnitTest.kt`

- [ ] **Step 1: Write WeightUnit test**

```kotlin
package ru.ainetico.honestprice.model

import org.junit.Assert.*
import org.junit.Test

class WeightUnitTest {

    @Test
    fun `G base unit is KG`() {
        assertEquals(WeightUnit.KG, WeightUnit.G.baseUnit)
    }

    @Test
    fun `KG base unit is null`() {
        assertNull(WeightUnit.KG.baseUnit)
    }

    @Test
    fun `ML base unit is L`() {
        assertEquals(WeightUnit.L, WeightUnit.ML.baseUnit)
    }

    @Test
    fun `L base unit is null`() {
        assertNull(WeightUnit.L.baseUnit)
    }

    @Test
    fun `PCS base unit is null`() {
        assertNull(WeightUnit.PCS.baseUnit)
    }

    @Test
    fun `all base units are themselves base`() {
        for (unit in WeightUnit.entries) {
            if (unit.baseUnit != null) {
                assertNull(unit.baseUnit!!.baseUnit)
            }
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.model.WeightUnitTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

- [ ] **Step 3: Create WeightUnit.kt**

```kotlin
package ru.ainetico.honestprice.model

enum class WeightUnit(val displayName: String, private val baseUnitName: String?) {
    KG("кг", null),
    G("г", "KG"),
    L("л", null),
    ML("мл", "L"),
    PCS("шт", null);

    val baseUnit: WeightUnit? get() = baseUnitName?.let { valueOf(it) }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.model.WeightUnitTest" --info 2>&1 | tail -20`
Expected: PASS (6 tests)

- [ ] **Step 5: Create remaining model classes**

`OcrBlock.kt`:

```kotlin
package ru.ainetico.honestprice.model

import android.graphics.Rect

data class OcrBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)
```

`OcrResult.kt`:

```kotlin
package ru.ainetico.honestprice.model

data class OcrResult(
    val blocks: List<OcrBlock>
)
```

`ParsedPriceTag.kt`:

```kotlin
package ru.ainetico.honestprice.model

import java.math.BigDecimal

data class ParsedPriceTag(
    val productName: String? = null,
    val priceRegular: BigDecimal? = null,
    val priceDiscount: BigDecimal? = null,
    val weightValue: BigDecimal? = null,
    val weightUnit: WeightUnit? = null,
    val barcode: String? = null,
    val rawBlocks: List<OcrBlock> = emptyList()
)
```

`PriceResult.kt`:

```kotlin
package ru.ainetico.honestprice.model

import java.math.BigDecimal

data class PriceResult(
    val pricePerUnit: BigDecimal,
    val pricePerUnitDiscount: BigDecimal?,
    val displayUnit: WeightUnit,
    val source: ParsedPriceTag
)
```

`AnalysisResult.kt`:

```kotlin
package ru.ainetico.honestprice.model

data class AnalysisResult(
    val tag: ParsedPriceTag,
    val price: PriceResult?
)
```

- [ ] **Step 6: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/model/ app/src/test/java/ru/ainetico/honestprice/model/
git commit -m "feat: add core model classes (WeightUnit, OcrBlock, ParsedPriceTag, PriceResult)"
```

---

## Task 3: BlockClassifier

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/parser/BlockRole.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/parser/BlockClassifier.kt`
- Test: `app/src/test/java/ru/ainetico/honestprice/parser/BlockClassifierTest.kt`

- [ ] **Step 1: Write BlockClassifier tests**

```kotlin
package ru.ainetico.honestprice.parser

import android.graphics.Rect
import ru.ainetico.honestprice.model.OcrBlock
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class BlockClassifierTest {

    private val classifier = BlockClassifier()

    @Test
    fun `classifies price with ruble sign`() {
        val block = OcrBlock("89.90 ₽", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies price with руб`() {
        val block = OcrBlock("123.45 руб", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies discount price near card keyword`() {
        val block = OcrBlock("по карте 69.90", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.DISCOUNT_PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies discount price with скидка`() {
        val block = OcrBlock("скидка 49.99 ₽", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.DISCOUNT_PRICE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in grams`() {
        val block = OcrBlock("500 г", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in kg`() {
        val block = OcrBlock("1.5 кг", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in ml`() {
        val block = OcrBlock("330 мл", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in liters`() {
        val block = OcrBlock("1 л", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies weight in pieces`() {
        val block = OcrBlock("10 шт", Rect(0, 200, 100, 250), 0.9f)
        assertEquals(BlockRole.WEIGHT, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies name in upper third`() {
        val block = OcrBlock("Молоко Простоквашино", Rect(0, 0, 300, 50), 0.9f)
        assertEquals(BlockRole.NAME, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies noise for dates`() {
        val block = OcrBlock("01.03.2026", Rect(0, 350, 100, 400), 0.9f)
        assertEquals(BlockRole.NOISE, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies abbreviated names`() {
        val block = OcrBlock("Филе кур. охл.", Rect(0, 10, 300, 50), 0.9f)
        assertEquals(BlockRole.NAME, classifier.classify(block, imageHeight = 400))
    }

    @Test
    fun `classifies price with р dot`() {
        val block = OcrBlock("199.00 р.", Rect(0, 100, 200, 200), 0.9f)
        assertEquals(BlockRole.PRICE, classifier.classify(block, imageHeight = 400))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.parser.BlockClassifierTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

- [ ] **Step 3: Create BlockRole.kt**

```kotlin
package ru.ainetico.honestprice.parser

enum class BlockRole {
    PRICE,
    DISCOUNT_PRICE,
    WEIGHT,
    NAME,
    NOISE
}
```

- [ ] **Step 4: Create BlockClassifier.kt**

```kotlin
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
```

- [ ] **Step 5: Run tests to verify they pass**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.parser.BlockClassifierTest" --info 2>&1 | tail -20`
Expected: PASS (13 tests)

- [ ] **Step 6: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/parser/ app/src/test/java/ru/ainetico/honestprice/parser/
git commit -m "feat: add BlockClassifier with role-based OCR block classification"
```

---

## Task 4: PriceTagParser

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/parser/PriceTagParser.kt`
- Test: `app/src/test/java/ru/ainetico/honestprice/parser/PriceTagParserTest.kt`

- [ ] **Step 1: Write PriceTagParser tests**

```kotlin
package ru.ainetico.honestprice.parser

import android.graphics.Rect
import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult
import ru.ainetico.honestprice.model.WeightUnit
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class PriceTagParserTest {

    private val parser = PriceTagParser()

    @Test
    fun `parses complete price tag`() {
        val blocks = listOf(
            OcrBlock("Молоко 3.2%", Rect(10, 10, 300, 50), 0.95f),
            OcrBlock("89.90 ₽", Rect(10, 100, 250, 180), 0.98f),
            OcrBlock("по карте 69.90", Rect(10, 200, 250, 260), 0.96f),
            OcrBlock("1 л", Rect(10, 270, 100, 300), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = "4607025392408")

        assertEquals("Молоко 3.2%", result.productName)
        assertEquals(BigDecimal("89.90"), result.priceRegular)
        assertEquals(BigDecimal("69.90"), result.priceDiscount)
        assertEquals(BigDecimal("1"), result.weightValue)
        assertEquals(WeightUnit.L, result.weightUnit)
        assertEquals("4607025392408", result.barcode)
    }

    @Test
    fun `parses tag with single price`() {
        val blocks = listOf(
            OcrBlock("Хлеб белый", Rect(10, 10, 300, 50), 0.95f),
            OcrBlock("45.00 ₽", Rect(10, 100, 250, 180), 0.98f),
            OcrBlock("500 г", Rect(10, 200, 100, 230), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals("Хлеб белый", result.productName)
        assertEquals(BigDecimal("45.00"), result.priceRegular)
        assertNull(result.priceDiscount)
        assertEquals(BigDecimal("500"), result.weightValue)
        assertEquals(WeightUnit.G, result.weightUnit)
        assertNull(result.barcode)
    }

    @Test
    fun `two prices without context - smaller is discount`() {
        val blocks = listOf(
            OcrBlock("Сок апельсиновый", Rect(10, 10, 300, 50), 0.95f),
            OcrBlock("129.90", Rect(10, 100, 250, 180), 0.98f),
            OcrBlock("99.90", Rect(10, 200, 250, 260), 0.96f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("129.90"), result.priceRegular)
        assertEquals(BigDecimal("99.90"), result.priceDiscount)
    }

    @Test
    fun `empty OCR result returns all nulls`() {
        val result = parser.parse(OcrResult(emptyList()), barcode = null)

        assertNull(result.productName)
        assertNull(result.priceRegular)
        assertNull(result.priceDiscount)
        assertNull(result.weightValue)
        assertNull(result.weightUnit)
    }

    @Test
    fun `parses weight in grams`() {
        val blocks = listOf(
            OcrBlock("250 гр", Rect(10, 200, 100, 230), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("250"), result.weightValue)
        assertEquals(WeightUnit.G, result.weightUnit)
    }

    @Test
    fun `parses price with comma separator`() {
        val blocks = listOf(
            OcrBlock("89,90 руб", Rect(10, 100, 250, 180), 0.98f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("89.90"), result.priceRegular)
    }

    @Test
    fun `parses weight in kg with decimal`() {
        val blocks = listOf(
            OcrBlock("0.5 кг", Rect(10, 200, 100, 230), 0.9f)
        )
        val result = parser.parse(OcrResult(blocks), barcode = null)

        assertEquals(BigDecimal("0.5"), result.weightValue)
        assertEquals(WeightUnit.KG, result.weightUnit)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.parser.PriceTagParserTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

- [ ] **Step 3: Create PriceTagParser.kt**

```kotlin
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
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.parser.PriceTagParserTest" --info 2>&1 | tail -20`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/parser/PriceTagParser.kt app/src/test/java/ru/ainetico/honestprice/parser/PriceTagParserTest.kt
git commit -m "feat: add PriceTagParser with heuristic extraction and conflict resolution"
```

---

## Task 5: PriceCalculator

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/calculator/PriceCalculator.kt`
- Test: `app/src/test/java/ru/ainetico/honestprice/calculator/PriceCalculatorTest.kt`

- [ ] **Step 1: Write PriceCalculator tests**

```kotlin
package ru.ainetico.honestprice.calculator

import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.math.RoundingMode

class PriceCalculatorTest {

    private val calculator = PriceCalculator()

    @Test
    fun `calculates price per kg from grams`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("50.00"),
            weightValue = BigDecimal("500"),
            weightUnit = WeightUnit.G
        )
        val result = calculator.calculate(tag)!!

        assertEquals(BigDecimal("100.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.KG, result.displayUnit)
    }

    @Test
    fun `calculates price per liter from ml`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("60.00"),
            weightValue = BigDecimal("330"),
            weightUnit = WeightUnit.ML
        )
        val result = calculator.calculate(tag)!!

        assertEquals(
            BigDecimal("181.82"),
            result.pricePerUnit.setScale(2, RoundingMode.HALF_UP)
        )
        assertEquals(WeightUnit.L, result.displayUnit)
    }

    @Test
    fun `returns price as-is for PCS`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("25.00"),
            weightValue = BigDecimal("6"),
            weightUnit = WeightUnit.PCS
        )
        val result = calculator.calculate(tag)!!

        assertEquals(BigDecimal("25.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.PCS, result.displayUnit)
    }

    @Test
    fun `returns null when no price`() {
        val tag = ParsedPriceTag(
            weightValue = BigDecimal("500"),
            weightUnit = WeightUnit.G
        )
        assertNull(calculator.calculate(tag))
    }

    @Test
    fun `handles no weight - treats as PCS`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("99.00")
        )
        val result = calculator.calculate(tag)!!

        assertEquals(BigDecimal("99.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.PCS, result.displayUnit)
    }

    @Test
    fun `calculates discount price per unit`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("100.00"),
            priceDiscount = BigDecimal("80.00"),
            weightValue = BigDecimal("500"),
            weightUnit = WeightUnit.G
        )
        val result = calculator.calculate(tag)!!

        assertEquals(BigDecimal("200.00"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(
            BigDecimal("160.00"),
            result.pricePerUnitDiscount!!.setScale(2, RoundingMode.HALF_UP)
        )
    }

    @Test
    fun `calculates price per kg from kg`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("250.00"),
            weightValue = BigDecimal("1.5"),
            weightUnit = WeightUnit.KG
        )
        val result = calculator.calculate(tag)!!

        assertEquals(
            BigDecimal("166.67"),
            result.pricePerUnit.setScale(2, RoundingMode.HALF_UP)
        )
        assertEquals(WeightUnit.KG, result.displayUnit)
    }

    @Test
    fun `custom target unit`() {
        val tag = ParsedPriceTag(
            priceRegular = BigDecimal("100.00"),
            weightValue = BigDecimal("1"),
            weightUnit = WeightUnit.KG
        )
        val result = calculator.calculate(tag, targetUnit = WeightUnit.G)!!

        assertEquals(BigDecimal("0.10"), result.pricePerUnit.setScale(2, RoundingMode.HALF_UP))
        assertEquals(WeightUnit.G, result.displayUnit)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.calculator.PriceCalculatorTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

- [ ] **Step 3: Create PriceCalculator.kt**

```kotlin
package ru.ainetico.honestprice.calculator

import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.PriceResult
import ru.ainetico.honestprice.model.WeightUnit
import java.math.BigDecimal
import java.math.MathContext
import java.math.RoundingMode

class PriceCalculator {

    companion object {
        private val MATH_CONTEXT = MathContext(10, RoundingMode.HALF_UP)

        private val CONVERSION_TO_BASE: Map<WeightUnit, BigDecimal> = mapOf(
            WeightUnit.G to BigDecimal("0.001"),    // 1g = 0.001kg
            WeightUnit.KG to BigDecimal.ONE,
            WeightUnit.ML to BigDecimal("0.001"),    // 1ml = 0.001l
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

        val weightUnit = tag.weightUnit ?: WeightUnit.PCS
        val weightValue = tag.weightValue ?: BigDecimal.ONE

        val baseUnit = weightUnit.baseUnit ?: weightUnit
        if (baseUnit == WeightUnit.PCS) {
            return PriceResult(
                pricePerUnit = priceRegular,
                pricePerUnitDiscount = tag.priceDiscount,
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
        val pricePerUnitDiscount = tag.priceDiscount?.divide(effectiveWeight, MATH_CONTEXT)

        return PriceResult(
            pricePerUnit = pricePerUnit,
            pricePerUnitDiscount = pricePerUnitDiscount,
            displayUnit = safeDisplayUnit,
            source = tag
        )
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.calculator.PriceCalculatorTest" --info 2>&1 | tail -20`
Expected: PASS (8 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/calculator/ app/src/test/java/ru/ainetico/honestprice/calculator/
git commit -m "feat: add PriceCalculator with unit normalization and conversion"
```

---

## Task 6: OcrEngine and BarcodeEngine

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/ocr/OcrEngine.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/ocr/BarcodeEngine.kt`

These wrap ML Kit APIs — tested via integration/instrumented tests, not unit tests. Error handling: catch all exceptions, return empty/null.

- [ ] **Step 1: Create OcrEngine.kt**

```kotlin
package ru.ainetico.honestprice.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.tasks.await
import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult

class OcrEngine {

    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

    suspend fun recognize(bitmap: Bitmap): OcrResult {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = recognizer.process(image).await()

            val blocks = visionText.textBlocks.flatMap { textBlock ->
                textBlock.lines.mapNotNull { line ->
                    val box = line.boundingBox ?: return@mapNotNull null
                    OcrBlock(
                        text = line.text,
                        boundingBox = box,
                        confidence = line.confidence ?: 0f
                    )
                }
            }

            OcrResult(blocks)
        } catch (e: Exception) {
            Log.e("OcrEngine", "OCR failed", e)
            OcrResult(emptyList())
        }
    }
}
```

- [ ] **Step 2: Create BarcodeEngine.kt**

```kotlin
package ru.ainetico.honestprice.ocr

import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.tasks.await

class BarcodeEngine {

    private val scanner = BarcodeScanning.getClient(
        BarcodeScannerOptions.Builder()
            .setBarcodeFormats(Barcode.FORMAT_EAN_13, Barcode.FORMAT_UPC_A)
            .build()
    )

    suspend fun scan(bitmap: Bitmap): String? {
        return try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val barcodes = scanner.process(image).await()
            barcodes.firstOrNull()?.rawValue
        } catch (e: Exception) {
            Log.e("BarcodeEngine", "Barcode scan failed", e)
            null
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/ocr/
git commit -m "feat: add OcrEngine and BarcodeEngine wrapping ML Kit with error handling"
```

---

## Task 7: ImagePreprocessor

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/image/ImagePreprocessor.kt`
- Test: `app/src/test/java/ru/ainetico/honestprice/image/ImagePreprocessorTest.kt`

- [ ] **Step 1: Write ImagePreprocessor tests**

```kotlin
package ru.ainetico.honestprice.image

import android.graphics.Bitmap
import android.graphics.Rect
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ImagePreprocessorTest {

    private val preprocessor = ImagePreprocessor()

    @Test
    fun `calculates inSampleSize 2 for 4000px image`() {
        // 4000 / 2 = 2000 >= 1080 ✓, 4000 / 4 = 1000 < 1080 ✗ → inSampleSize = 2
        assertEquals(2, preprocessor.calculateInSampleSize(4000, 3000))
    }

    @Test
    fun `calculates inSampleSize 1 for 1920px image`() {
        assertEquals(1, preprocessor.calculateInSampleSize(1920, 1080))
    }

    @Test
    fun `calculates inSampleSize 1 for small image`() {
        assertEquals(1, preprocessor.calculateInSampleSize(800, 600))
    }

    @Test
    fun `calculates inSampleSize 4 for 8000px image`() {
        // 8000 / 4 = 2000 >= 1080 ✓, 8000 / 8 = 1000 < 1080 ✗ → inSampleSize = 4
        assertEquals(4, preprocessor.calculateInSampleSize(8000, 6000))
    }

    @Test
    fun `crops bitmap correctly`() {
        val bitmap = Bitmap.createBitmap(1000, 1000, Bitmap.Config.ARGB_8888)
        val crop = Rect(100, 100, 500, 500)
        val result = preprocessor.cropBitmap(bitmap, crop)

        assertEquals(400, result.width)
        assertEquals(400, result.height)
    }

    @Test
    fun `processBitmap downsamples large image`() {
        val largeBitmap = Bitmap.createBitmap(4000, 3000, Bitmap.Config.ARGB_8888)
        val result = preprocessor.processBitmap(largeBitmap, cropRect = null)

        assertTrue(
            "Short side should be >= 1080",
            minOf(result.width, result.height) >= 1080
        )
        assertTrue(
            "Should be smaller than original",
            result.width < 4000 || result.height < 3000
        )
    }

    @Test
    fun `processBitmap does not upsample small image`() {
        val smallBitmap = Bitmap.createBitmap(800, 600, Bitmap.Config.ARGB_8888)
        val result = preprocessor.processBitmap(smallBitmap, cropRect = null)

        assertEquals(800, result.width)
        assertEquals(600, result.height)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.image.ImagePreprocessorTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

- [ ] **Step 3: Create ImagePreprocessor.kt**

Two entry points: `processFile()` for camera/gallery (uses `inSampleSize` + EXIF rotation to avoid OOM), `processBitmap()` for already-decoded bitmaps.

```kotlin
package ru.ainetico.honestprice.image

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import androidx.exifinterface.media.ExifInterface

class ImagePreprocessor {

    companion object {
        const val MIN_SHORT_SIDE = 1080
    }

    /**
     * Primary entry point for camera/gallery images. Decodes with inSampleSize
     * to avoid OOM, applies EXIF rotation, then optional crop.
     */
    fun processFile(filePath: String, cropRect: Rect?): Bitmap {
        val (width, height) = getImageDimensions(filePath)
        val inSampleSize = calculateInSampleSize(width, height)

        val options = BitmapFactory.Options().apply {
            this.inSampleSize = inSampleSize
        }
        val bitmap = BitmapFactory.decodeFile(filePath, options)
        val rotated = applyExifRotation(bitmap, filePath)
        val cropped = cropRect?.let { cropBitmap(rotated, it) } ?: rotated
        return cropped
    }

    /**
     * Fallback for already-decoded bitmaps (e.g., from content:// URI).
     * Software downsampling — use processFile when possible.
     */
    fun processBitmap(bitmap: Bitmap, cropRect: Rect?): Bitmap {
        val cropped = cropRect?.let { cropBitmap(bitmap, it) } ?: bitmap
        return downsample(cropped)
    }

    fun calculateInSampleSize(width: Int, height: Int): Int {
        val shortSide = minOf(width, height)
        var inSampleSize = 1
        while (shortSide / (inSampleSize * 2) >= MIN_SHORT_SIDE) {
            inSampleSize *= 2
        }
        return inSampleSize
    }

    fun cropBitmap(bitmap: Bitmap, rect: Rect): Bitmap {
        val safeRect = Rect(
            rect.left.coerceIn(0, bitmap.width),
            rect.top.coerceIn(0, bitmap.height),
            rect.right.coerceIn(0, bitmap.width),
            rect.bottom.coerceIn(0, bitmap.height)
        )
        return Bitmap.createBitmap(
            bitmap,
            safeRect.left,
            safeRect.top,
            safeRect.width(),
            safeRect.height()
        )
    }

    private fun getImageDimensions(filePath: String): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(filePath, options)
        return options.outWidth to options.outHeight
    }

    private fun applyExifRotation(bitmap: Bitmap, filePath: String): Bitmap {
        val exif = ExifInterface(filePath)
        val orientation = exif.getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_NORMAL
        )
        val degrees = when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> 90f
            ExifInterface.ORIENTATION_ROTATE_180 -> 180f
            ExifInterface.ORIENTATION_ROTATE_270 -> 270f
            else -> return bitmap
        }
        val matrix = Matrix().apply { postRotate(degrees) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    private fun downsample(bitmap: Bitmap): Bitmap {
        val shortSide = minOf(bitmap.width, bitmap.height)
        if (shortSide <= MIN_SHORT_SIDE) return bitmap

        val scale = MIN_SHORT_SIDE.toFloat() / shortSide
        val newWidth = (bitmap.width * scale).toInt()
        val newHeight = (bitmap.height * scale).toInt()
        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.image.ImagePreprocessorTest" --info 2>&1 | tail -20`
Expected: PASS (7 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/image/ app/src/test/java/ru/ainetico/honestprice/image/
git commit -m "feat: add ImagePreprocessor with crop and downsampling"
```

---

## Task 8: ImageAnalyzer (orchestrator)

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/analyzer/ImageAnalyzer.kt`
- Test: `app/src/test/java/ru/ainetico/honestprice/analyzer/ImageAnalyzerTest.kt`

- [ ] **Step 1: Write ImageAnalyzer tests**

```kotlin
package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.image.ImagePreprocessor
import ru.ainetico.honestprice.model.OcrBlock
import ru.ainetico.honestprice.model.OcrResult
import ru.ainetico.honestprice.model.WeightUnit
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.ocr.OcrEngine
import ru.ainetico.honestprice.parser.PriceTagParser
import java.math.BigDecimal

@RunWith(RobolectricTestRunner::class)
class ImageAnalyzerTest {

    private val preprocessor = mockk<ImagePreprocessor>()
    private val ocrEngine = mockk<OcrEngine>()
    private val barcodeEngine = mockk<BarcodeEngine>()
    private val parser = PriceTagParser()
    private val calculator = PriceCalculator()

    private val analyzer = ImageAnalyzer(
        preprocessor, ocrEngine, barcodeEngine, parser, calculator
    )

    @Test
    fun `analyze returns complete result`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { preprocessor.processBitmap(any(), any()) } returns bitmap
        coEvery { ocrEngine.recognize(any()) } returns OcrResult(
            listOf(
                OcrBlock("Молоко", Rect(0, 0, 100, 30), 0.9f),
                OcrBlock("89.90 ₽", Rect(0, 50, 100, 80), 0.9f),
                OcrBlock("1 л", Rect(0, 80, 50, 100), 0.9f)
            )
        )
        coEvery { barcodeEngine.scan(any()) } returns "4607025392408"

        val result = analyzer.analyze(bitmap, null)

        assertEquals("Молоко", result.tag.productName)
        assertEquals(BigDecimal("89.90"), result.tag.priceRegular)
        assertEquals(WeightUnit.L, result.tag.weightUnit)
        assertEquals("4607025392408", result.tag.barcode)
        assertNotNull(result.price)
        assertEquals(WeightUnit.L, result.price!!.displayUnit)
    }

    @Test
    fun `analyze returns null price when no price detected`() = runTest {
        val bitmap = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888)
        coEvery { preprocessor.processBitmap(any(), any()) } returns bitmap
        coEvery { ocrEngine.recognize(any()) } returns OcrResult(emptyList())
        coEvery { barcodeEngine.scan(any()) } returns null

        val result = analyzer.analyze(bitmap, null)

        assertNull(result.tag.priceRegular)
        assertNull(result.price)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.analyzer.ImageAnalyzerTest" --info 2>&1 | tail -20`
Expected: FAIL — class not found

- [ ] **Step 3: Create ImageAnalyzer.kt**

```kotlin
package ru.ainetico.honestprice.analyzer

import android.graphics.Bitmap
import android.graphics.Rect
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.image.ImagePreprocessor
import ru.ainetico.honestprice.model.AnalysisResult
import ru.ainetico.honestprice.ocr.BarcodeEngine
import ru.ainetico.honestprice.ocr.OcrEngine
import ru.ainetico.honestprice.parser.PriceTagParser

class ImageAnalyzer(
    private val preprocessor: ImagePreprocessor,
    private val ocrEngine: OcrEngine,
    private val barcodeEngine: BarcodeEngine,
    private val parser: PriceTagParser,
    private val calculator: PriceCalculator
) {
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?): AnalysisResult =
        coroutineScope {
            val processed = preprocessor.processBitmap(bitmap, cropRect)

            val ocrDeferred = async { ocrEngine.recognize(processed) }
            val barcodeDeferred = async { barcodeEngine.scan(processed) }

            val ocrResult = ocrDeferred.await()
            val barcode = barcodeDeferred.await()

            val tag = parser.parse(ocrResult, barcode)
            val price = calculator.calculate(tag)

            AnalysisResult(tag = tag, price = price)
        }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --tests "ru.ainetico.honestprice.analyzer.ImageAnalyzerTest" --info 2>&1 | tail -20`
Expected: PASS (2 tests)

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/analyzer/ app/src/test/java/ru/ainetico/honestprice/analyzer/
git commit -m "feat: add ImageAnalyzer orchestrating OCR + barcode + parser + calculator"
```

---

## Task 9: Room entities, DAO, and ScanRepository

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/data/ScanStatus.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/SyncStatus.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/Scan.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/Store.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/ScanDao.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/StoreDao.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/Converters.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/AppDatabase.kt`
- Create: `app/src/main/java/ru/ainetico/honestprice/data/ScanRepository.kt`

Minimal persistence layer needed by ImageAnalyzer crash-recovery. Full DAO expansion will happen in the DB+History subsystem.

- [ ] **Step 1: Create ScanStatus.kt and SyncStatus.kt**

`ScanStatus.kt`:

```kotlin
package ru.ainetico.honestprice.data

enum class ScanStatus {
    PROCESSING,
    COMPLETED,
    EDITED
}
```

`SyncStatus.kt`:

```kotlin
package ru.ainetico.honestprice.data

enum class SyncStatus {
    LOCAL_ONLY
}
```

- [ ] **Step 2: Create Scan.kt (Room entity)**

```kotlin
package ru.ainetico.honestprice.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal

@Entity(tableName = "scans")
data class Scan(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val status: ScanStatus = ScanStatus.PROCESSING,
    val imagePath: String? = null,
    val thumbnailPath: String? = null,
    val productName: String? = null,
    val priceRegular: String? = null,
    val priceDiscount: String? = null,
    val weightValue: String? = null,
    val weightUnit: String? = null,
    val barcode: String? = null,
    val storeName: String? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val pricePerUnit: String? = null,
    val pricePerUnitDiscount: String? = null,
    val displayUnit: String? = null,
    val syncStatus: SyncStatus = SyncStatus.LOCAL_ONLY,
    val createdAt: Long = System.currentTimeMillis()
)
```

Note: BigDecimal stored as String in Room for precision. Conversion helpers in repository.

- [ ] **Step 3: Create Store.kt (Room entity)**

```kotlin
package ru.ainetico.honestprice.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stores")
data class Store(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String
)
```

- [ ] **Step 4: Create ScanDao.kt**

```kotlin
package ru.ainetico.honestprice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update

@Dao
interface ScanDao {
    @Insert
    suspend fun insert(scan: Scan): Long

    @Update
    suspend fun update(scan: Scan)

    @Query("SELECT * FROM scans WHERE id = :id")
    suspend fun getById(id: Long): Scan?

    @Query("SELECT * FROM scans WHERE status = 'PROCESSING'")
    suspend fun getProcessingScans(): List<Scan>

    @Query("SELECT * FROM scans ORDER BY createdAt DESC")
    suspend fun getAllScans(): List<Scan>
}
```

- [ ] **Step 5: Create StoreDao.kt**

```kotlin
package ru.ainetico.honestprice.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface StoreDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(store: Store): Long

    @Query("SELECT * FROM stores ORDER BY name ASC")
    suspend fun getAllStores(): List<Store>

    @Query("SELECT * FROM stores WHERE name LIKE '%' || :query || '%' ORDER BY name ASC")
    suspend fun search(query: String): List<Store>
}
```

- [ ] **Step 6: Create Converters.kt (TypeConverters for Room enums)**

```kotlin
package ru.ainetico.honestprice.data

import androidx.room.TypeConverter

class Converters {
    @TypeConverter
    fun fromScanStatus(value: ScanStatus): String = value.name

    @TypeConverter
    fun toScanStatus(value: String): ScanStatus = ScanStatus.valueOf(value)

    @TypeConverter
    fun fromSyncStatus(value: SyncStatus): String = value.name

    @TypeConverter
    fun toSyncStatus(value: String): SyncStatus = SyncStatus.valueOf(value)
}
```

- [ ] **Step 7: Create AppDatabase.kt**

```kotlin
package ru.ainetico.honestprice.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [Scan::class, Store::class], version = 1)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scanDao(): ScanDao
    abstract fun storeDao(): StoreDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "honest_price.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
```

- [ ] **Step 8: Create ScanRepository.kt**

```kotlin
package ru.ainetico.honestprice.data

import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.PriceResult

interface ScanRepository {
    suspend fun createProcessing(imagePath: String): Long
    suspend fun markCompleted(scanId: Long, tag: ParsedPriceTag, price: PriceResult?)
    suspend fun getProcessingScans(): List<Scan>
}

class ScanRepositoryImpl(private val scanDao: ScanDao) : ScanRepository {

    override suspend fun createProcessing(imagePath: String): Long {
        return scanDao.insert(Scan(imagePath = imagePath))
    }

    override suspend fun markCompleted(scanId: Long, tag: ParsedPriceTag, price: PriceResult?) {
        val existing = scanDao.getById(scanId) ?: return
        scanDao.update(
            existing.copy(
                status = ScanStatus.COMPLETED,
                productName = tag.productName,
                priceRegular = tag.priceRegular?.toPlainString(),
                priceDiscount = tag.priceDiscount?.toPlainString(),
                weightValue = tag.weightValue?.toPlainString(),
                weightUnit = tag.weightUnit?.name,
                barcode = tag.barcode,
                pricePerUnit = price?.pricePerUnit?.toPlainString(),
                pricePerUnitDiscount = price?.pricePerUnitDiscount?.toPlainString(),
                displayUnit = price?.displayUnit?.name
            )
        )
    }

    override suspend fun getProcessingScans(): List<Scan> {
        return scanDao.getProcessingScans()
    }
}
```

- [ ] **Step 9: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 10: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/data/
git commit -m "feat: add Room entities, DAO, AppDatabase, and ScanRepository for crash-recovery"
```

---

## Task 10: LocationProvider

**Files:**
- Create: `app/src/main/java/ru/ainetico/honestprice/location/LocationProvider.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] **Step 1: Add location permission to AndroidManifest.xml**

Add before `<application>`:

```xml
<uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
<uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
```

- [ ] **Step 2: Create LocationProvider.kt**

```kotlin
package ru.ainetico.honestprice.location

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.util.Log
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class LocationProvider(context: Context) {

    private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

    @SuppressLint("MissingPermission")
    suspend fun getCurrentLocation(): Location? {
        return try {
            val cancellationToken = CancellationTokenSource()
            fusedClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationToken.token
            ).await()
        } catch (e: SecurityException) {
            Log.w("LocationProvider", "Location permission not granted", e)
            null
        } catch (e: Exception) {
            Log.e("LocationProvider", "Failed to get location", e)
            null
        }
    }
}
```

- [ ] **Step 3: Verify build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/ru/ainetico/honestprice/location/ app/src/main/AndroidManifest.xml
git commit -m "feat: add LocationProvider with fine location for store detection"
```

---

## Task 11: Run all tests and verify

- [ ] **Step 1: Run all unit tests**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew test --info 2>&1 | tail -30`
Expected: All tests PASS

- [ ] **Step 2: Verify full build**

Run: `cd /home/netsvetoch/AndroidStudioProjects && ./gradlew assembleDebug 2>&1 | tail -5`
Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Final commit if any fixes needed**

Only if tests revealed issues that required fixes.
