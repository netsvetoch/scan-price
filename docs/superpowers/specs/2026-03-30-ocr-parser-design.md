# ЧестнаяЦена — OCR + Парсер: Спецификация подсистемы

**Дата:** 2026-03-30
**Подсистема:** OCR-движок, парсер ценников, калькулятор цены за единицу
**Статус:** MVP / offline-first / open-source

---

## 1. Обзор

Первая подсистема приложения «ЧестнаяЦена». Отвечает за захват изображения ценника (камера, галерея или ручной ввод), распознавание текста и штрихкода, извлечение структурированных данных и вычисление «честной цены» за единицу веса/объёма.

### Принципы

- **Offline-only** — вся обработка на устройстве, никаких сетевых запросов
- **Privacy-first** — фото и данные не покидают устройство
- **Graceful degradation** — обработка всегда успешна, парсер заполняет от 0 до всех полей, остальное вводит пользователь
- **Производительность** — отзывчивый UI на слабых устройствах, вся тяжёлая работа в фоне
- **Устойчивость к крашам** — данные сохраняются как можно раньше, при повторном запуске обработка продолжается
- **i18n-ready** — все строки через ресурсы, готовность к татарскому, чеченскому, башкирскому и другим языкам

---

## 2. Архитектура

```
Камера / Галерея / Ручной ввод
        |
        v  Bitmap (или null при ручном вводе)
  ImagePreprocessor        — кроп, автоповорот по EXIF, inSampleSize
        |
        v  Bitmap
   +---------+-----------+
   |                     |
OcrEngine          BarcodeEngine     (параллельно, coroutines)
   |                     |
   v                     v
OcrResult            String?         (EAN-13 / UPC-A)
   +----------+----------+
              |
              v
       PriceTagParser              — эвристики, regex, классификация блоков
              |
              v
        ParsedPriceTag
              |
              v
        PriceCalculator            — нормализация единиц, цена за кг/л/шт
              |
              v
         PriceResult
              |
              v
         UI (экран результата / редактирование)
```

### Три точки входа — один экран результата

```
Камера     → ImagePreprocessor → OCR → Экран результата (поля заполнены)
Галерея    → ImagePreprocessor → OCR → Экран результата (поля заполнены)
Вручную    →                          Экран результата (поля пустые)
```

---

## 3. Модели данных

### OcrBlock

```kotlin
data class OcrBlock(
    val text: String,
    val boundingBox: Rect,
    val confidence: Float
)
```

### OcrResult

```kotlin
data class OcrResult(
    val blocks: List<OcrBlock>
)
```

### WeightUnit

```kotlin
enum class WeightUnit(val displayName: String, val baseUnit: WeightUnit?) {
    G("г", KG), KG("кг", null),
    ML("мл", L), L("л", null),
    PCS("шт", null);
}
```

### ParsedPriceTag

```kotlin
data class ParsedPriceTag(
    val productName: String?,
    val priceRegular: BigDecimal?,
    val priceDiscount: BigDecimal?,
    val weightValue: BigDecimal?,
    val weightUnit: WeightUnit?,
    val barcode: String?,
    val storeName: String?,
    val latitude: Double?,
    val longitude: Double?,
    val rawBlocks: List<OcrBlock>
)
```

### PriceResult

```kotlin
data class PriceResult(
    val pricePerUnit: BigDecimal,
    val pricePerUnitDiscount: BigDecimal?,
    val displayUnit: WeightUnit,
    val source: ParsedPriceTag
)
```

### ScanStatus

```kotlin
enum class ScanStatus {
    PROCESSING,   // фото сохранено, OCR ещё не завершён
    COMPLETED,    // распознано (полностью или частично)
    EDITED        // пользователь отредактировал поля
}
```

### SyncStatus (для будущей серверной синхронизации)

```kotlin
enum class SyncStatus {
    LOCAL_ONLY,       // MVP — все записи в этом статусе
    PENDING_UPLOAD,
    UPLOADED,
    UPLOAD_FAILED
}
```

### ScanImage

```kotlin
data class ScanImage(
    val originalPath: String,
    val thumbnailPath: String,
    val syncStatus: SyncStatus,
    val serverUrl: String?
)
```

### Store

```kotlin
data class Store(
    val id: Long = 0,
    val name: String
)
```

---

## 4. Компоненты

### 4.1 ImagePreprocessor

```kotlin
class ImagePreprocessor {
    fun process(bitmap: Bitmap, cropRect: Rect?): Bitmap
}
```

**На уровне камеры (Camera2 через CameraX Interop):**
- `CONTROL_AE_MODE_ON` с компенсацией экспозиции вверх (ценники в тени полки)
- `CONTROL_AWB_MODE_FLUORESCENT` (магазинное освещение)
- `CONTROL_AF_MODE_CONTINUOUS_PICTURE` (резкий текст)
- `NOISE_REDUCTION_MODE_HIGH_QUALITY` (мелкий текст)

**На уровне Bitmap:**
- Кроп по рамке видоискателя (для камеры), без кропа для галереи
- Автоповорот по EXIF-данным
- Загрузка с `inSampleSize` — не декодировать полное разрешение для OCR, достаточно ~2MP

### 4.2 OcrEngine

```kotlin
class OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult
}
```

- ML Kit Text Recognition v2 (`TextRecognition.getClient()`)
- Маппинг: ML Kit `TextBlock` → `Line` → наши `OcrBlock` (уровень Line — оптимальная гранулярность для ценников)
- Кириллица + латиница: ML Kit обрабатывает смешанный текст (Snickers, Whiskas на ценниках)

### 4.3 BarcodeEngine

```kotlin
class BarcodeEngine {
    suspend fun scan(bitmap: Bitmap): String?
}
```

- ML Kit Barcode Scanning
- Форматы: `FORMAT_EAN_13`, `FORMAT_UPC_A`
- Возвращает `null` если штрихкод не найден

### 4.4 PriceTagParser

```kotlin
class PriceTagParser {
    fun parse(ocrResult: OcrResult, barcode: String?): ParsedPriceTag
}
```

**Шаг 1 — Нормализация текста:**
- Нижний регистр
- Нормализация кавычек, тире, пробелов

**Шаг 2 — Классификация блоков по роли:**

| Роль | Как определяем |
|------|---------------|
| PRICE | Regex `\d+[.,]\d{2}` + контекст: `₽`, `руб`, `р.`, `р`, или без контекста но с крупным bounding box |
| DISCOUNT_PRICE | Тот же паттерн цены + близость к словам: `карт`, `скидк`, `цена для вас`, `по карте`, `выгода` |
| WEIGHT | Regex `\d+[.,]?\d*\s*(г|гр|кг|мл|л|шт|уп)\.?` |
| NAME | Текстовый блок в верхней трети ценника, не классифицированный как цена/вес |
| NOISE | Всё остальное (даты, адреса, артикулы) |

**Шаг 3 — Разрешение конфликтов:**
- Две цены без контекста: меньшая = `priceDiscount`, большая = `priceRegular`
- Одна цена: записывается как `priceRegular`, `priceDiscount` = `null`
- Более двух цен: берём две с наибольшими bounding box, меньшая = discount
- Несколько значений веса: ближайшее к цене по координатам
- Ничего не найдено: все поля `null` — пользователь заполнит вручную

**Единицы измерения — regex вынесен в конфиг** для будущей поддержки ценников на национальных языках.

**Сокращения** (Филе кур. охл.): парсер не раскрывает, сохраняет как есть. Точки в сокращениях не сбивают regex цены — блоки классифицированы по ролям.

### 4.5 PriceCalculator

```kotlin
class PriceCalculator {
    fun calculate(tag: ParsedPriceTag, targetUnit: WeightUnit? = null): PriceResult?
}
```

**Алгоритм:**
1. Определить базовую единицу: г/кг → KG, мл/л → L, шт/уп/null → PCS
2. Привести вес к базовой: 500г → 0.5кг, 330мл → 0.33л
3. `pricePerUnit = priceRegular / weightInBaseUnit`
4. Если есть `priceDiscount`: `pricePerUnitDiscount = priceDiscount / weightInBaseUnit`
5. Вес не указан или PCS: `pricePerUnit = priceRegular`, единица = PCS

**Переключение единицы пользователем:** конвертация только внутри группы (г↔кг, мл↔л). Между кг и л — невозможно.

### 4.6 ImageAnalyzer (оркестратор)

```kotlin
class ImageAnalyzer(
    private val preprocessor: ImagePreprocessor,
    private val ocrEngine: OcrEngine,
    private val barcodeEngine: BarcodeEngine,
    private val parser: PriceTagParser,
    private val calculator: PriceCalculator
) {
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?): ParsedPriceTag
}
```

- OCR и Barcode запускаются параллельно через `coroutineScope { async {} }`
- Всегда возвращает `ParsedPriceTag` (поля от 0 до всех заполнены)

---

## 5. Хранение изображений

### Файловая структура

```
filesDir/
  images/
    originals/     — исходное фото (полное разрешение, JPEG)
    thumbnails/    — превью для списка истории (200px по ширине)
```

- Имя файла: `{scanId}_{timestamp}.jpg`
- Хранение во внутреннем хранилище (`filesDir`) — не требует разрешений, недоступно другим приложениям
- Оригинал сохраняется всегда — для будущей серверной обработки и повторного OCR

### Импорт из галереи

```
Галерея → content:// URI
  → копируем в images/originals/{scanId}_{timestamp}.jpg
  → генерируем thumbnail
  → работаем только с локальной копией
```

URI из галереи временный — копирование гарантирует доступ к файлу.

### Ручной ввод

Фото опционально. Если не прикреплено — `originalPath` и `thumbnailPath` = `null`.

---

## 6. Выбор магазина

- Combobox: текстовое поле с автодополнением из истории + возможность ввести новое название
- Никаких предзаданных значений
- Таблица `stores` в Room наполняется пользователем
- Новый магазин автоматически добавляется при первом использовании

---

## 7. Геолокация

- Разрешение: `ACCESS_FINE_LOCATION` (точность важна для ТЦ с несколькими магазинами)
- Если пользователь отказал — координаты `null`, приложение работает без них
- Координаты сохраняются в Room для будущих фич (автоопределение магазина, карта цен)

---

## 8. Производительность и устойчивость

### Фоновая обработка

```
UI Thread              Background (Dispatchers.IO)
──────────             ──────────────────────────
Нажал "снять" →        1. Сохранить фото в filesDir
Показать спиннер       2. Создать запись в Room (status=PROCESSING)
                       3. OCR + Barcode (параллельно)
                       4. Парсинг (~мс)
                       5. Генерация thumbnail
← Показать карточку    6. Обновить запись (status=COMPLETED)
```

### Устойчивость к крашам

Принцип: **сохраняй как можно раньше**.

1. Фото сохранено в filesDir → запись создана в Room (`PROCESSING`)
2. Если краш → при запуске: `SELECT * FROM scans WHERE status = 'PROCESSING'` → повторить OCR
3. OCR завершён → обновить запись (`COMPLETED`)

### Оптимизация для слабых устройств

- Bitmap: загрузка с `inSampleSize` (~2MP для OCR, не полное разрешение)
- Thumbnail: 200px по ширине, отдельный файл
- ML Kit: использует GPU/NNAPI где доступно
- Room: все запросы через `suspend` + `Dispatchers.IO`
- `bitmap.recycle()` после использования, без статических ссылок на Bitmap

### Отзывчивость UI

- Навигация никогда не блокируется обработкой
- Пользователь может уйти с экрана — обработка продолжается в ViewModel
- Список истории: `Flow<List<Scan>>` из Room, реактивное обновление
- Skeleton/shimmer при загрузке

---

## 9. Интернационализация (i18n)

- Все строки через `strings.xml`, никаких хардкод-строк
- Русский по умолчанию, готовность к добавлению: татарский (`values-tt`), чеченский (`values-ce`), башкирский (`values-ba`)
- Regex единиц измерения в парсере — вынесены в конфиг для расширения
- Форматирование чисел и валюты — `NumberFormat.getInstance(locale)`
- Выбор языка внутри приложения — `AppCompatDelegate.setApplicationLocales()`

---

## 10. Зависимости

```
ML Kit Text Recognition  — OCR на устройстве
ML Kit Barcode Scanning  — сканирование штрихкодов
CameraX                  — камера + превью
CameraX Camera2 Interop  — тонкая настройка параметров съёмки
Room                     — локальная БД
Kotlin Coroutines        — асинхронная обработка
```

---

## 11. Что НЕ входит в эту подсистему (следующие этапы)

- Room-схема и DAO (подсистема «БД + История»)
- UI экранов (подсистема «UI»)
- Сравнение товаров по штрихкоду (подсистема «БД + История»)
- Серверная обработка и подписка (будущий релиз)
- Автоопределение магазина по координатам (будущий релиз)
