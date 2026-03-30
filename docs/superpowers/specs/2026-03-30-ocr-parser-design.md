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
enum class WeightUnit(val displayName: String, val baseUnitName: String?) {
    KG("кг", null),
    G("г", "KG"),
    L("л", null),
    ML("мл", "L"),
    PCS("шт", null);

    val baseUnit: WeightUnit? get() = baseUnitName?.let { valueOf(it) }
}
```

### ParsedPriceTag

Результат работы парсера. Поля `storeName`, `latitude`, `longitude` не заполняются парсером — они устанавливаются на уровне UI/ViewModel перед сохранением в Room.

```kotlin
data class ParsedPriceTag(
    val productName: String?,
    val priceRegular: BigDecimal?,
    val priceDiscount: BigDecimal?,
    val weightValue: BigDecimal?,
    val weightUnit: WeightUnit?,
    val barcode: String?,
    val rawBlocks: List<OcrBlock>
)
```

### AnalysisResult

Составной результат работы `ImageAnalyzer` — содержит и распознанные данные, и вычисленную цену за единицу.

```kotlin
data class AnalysisResult(
    val tag: ParsedPriceTag,
    val price: PriceResult?    // null если priceRegular не распознана
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

### Scan (минимальная сущность для crash-recovery)

Полная Room-схема определяется в подсистеме «БД + История». Здесь — минимальный контракт, необходимый для `ImageAnalyzer` и crash-recovery.

```kotlin
data class Scan(
    val id: Long = 0,
    val status: ScanStatus,
    val imagePath: String?,       // путь к оригиналу в filesDir
    val thumbnailPath: String?,
    val createdAt: Long           // System.currentTimeMillis()
)

enum class ScanStatus {
    PROCESSING,   // фото сохранено, OCR ещё не завершён
    COMPLETED,    // распознано (полностью или частично)
    EDITED        // пользователь отредактировал поля
}
```

### ScanRepository (интерфейс для persistence)

`ImageAnalyzer` работает с persistence через этот интерфейс. Реализация — в подсистеме «БД + История».

```kotlin
interface ScanRepository {
    suspend fun createProcessing(imagePath: String): Long   // возвращает scanId
    suspend fun markCompleted(scanId: Long, tag: ParsedPriceTag, price: PriceResult?)
    suspend fun getProcessingScans(): List<Scan>
}
```

### SyncStatus

В MVP используется только `LOCAL_ONLY`. Остальные значения будут добавлены через Room-миграцию в подсистеме серверной синхронизации (будущий релиз). В текущей подсистеме `SyncStatus` не используется.

```kotlin
enum class SyncStatus {
    LOCAL_ONLY
}
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
- Даунсемплинг для OCR: считать размеры исходного изображения, вычислить `inSampleSize` как максимальную степень двойки, при которой короткая сторона >= 1080px. Минимальный порог — 1080px по короткой стороне (ниже страдает распознавание мелкого текста)

### 4.2 OcrEngine

```kotlin
class OcrEngine {
    suspend fun recognize(bitmap: Bitmap): OcrResult
}
```

- ML Kit Text Recognition v2 (`TextRecognition.getClient()`)
- Маппинг: ML Kit `TextBlock` → `Line` → наши `OcrBlock` (уровень Line — оптимальная гранулярность для ценников)
- Кириллица + латиница: ML Kit обрабатывает смешанный текст (Snickers, Whiskas на ценниках)
- **Обработка ошибок:** при любом исключении ML Kit (MlKitException, OOM и т.д.) возвращает `OcrResult(emptyList())`. Ошибка логируется, но не прерывает поток — graceful degradation, пользователь заполнит поля вручную.

### 4.3 BarcodeEngine

```kotlin
class BarcodeEngine {
    suspend fun scan(bitmap: Bitmap): String?
}
```

- ML Kit Barcode Scanning
- Форматы: `FORMAT_EAN_13`, `FORMAT_UPC_A`
- Возвращает `null` если штрихкод не найден
- **Обработка ошибок:** при любом исключении возвращает `null`. Отсутствие штрихкода — нормальная ситуация, не ошибка.

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
- Если хотя бы одна цена классифицирована как `DISCOUNT_PRICE` на шаге 2 — использовать эту классификацию
- Если две цены найдены и обе без контекста скидки: **считаем неоднозначными** — меньшая предварительно назначается как `priceDiscount`, большая как `priceRegular`, но на экране результата оба поля подсвечиваются для проверки пользователем
- Одна цена: записывается как `priceRegular`, `priceDiscount` = `null`
- Более двух цен: берём две с наибольшими bounding box, применяем те же правила
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

Возвращает `null` **только** если `tag.priceRegular == null` (цена не распознана — нечего считать).

**Алгоритм:**
1. Если `priceRegular == null` → вернуть `null`
2. Определить базовую единицу: г/кг → KG, мл/л → L, шт/уп/null → PCS
3. Привести вес к базовой: 500г → 0.5кг, 330мл → 0.33л
4. `pricePerUnit = priceRegular / weightInBaseUnit`
5. Если есть `priceDiscount`: `pricePerUnitDiscount = priceDiscount / weightInBaseUnit`
6. Вес не указан или PCS: `pricePerUnit = priceRegular`, единица = PCS

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
    suspend fun analyze(bitmap: Bitmap, cropRect: Rect?): AnalysisResult
}
```

- OCR и Barcode запускаются параллельно через `coroutineScope { async {} }`
- Всегда возвращает `AnalysisResult` с заполненным `tag` (от 0 до всех полей) и `price` (`null` если цена не распознана)
- **Не зависит от persistence напрямую.** Сохранение в Room выполняется на уровне ViewModel через `ScanRepository`:

```
ViewModel:
  1. scanRepository.createProcessing(imagePath)  → получить scanId
  2. imageAnalyzer.analyze(bitmap, cropRect)      → получить AnalysisResult
  3. scanRepository.markCompleted(scanId, tag, price)
```

---

## 5. Хранение изображений

### Файловая структура

```
filesDir/
  images/
    originals/     — исходное фото (полное разрешение, JPEG)
    thumbnails/    — превью для списка истории (200px по ширине)
```

- Имя файла: `{scanId}_{timestamp}.jpg` — `scanId` получен от `ScanRepository.createProcessing()` до начала обработки
- Хранение во внутреннем хранилище (`filesDir`) — не требует разрешений, недоступно другим приложениям
- Оригинал сохраняется всегда — для будущей серверной обработки и повторного OCR

### Импорт из галереи

```
Галерея → content:// URI
  → ScanRepository.createProcessing() → scanId
  → копируем в images/originals/{scanId}_{timestamp}.jpg
  → генерируем thumbnail
  → работаем только с локальной копией
```

URI из галереи временный — копирование гарантирует доступ к файлу.

### Ручной ввод

Фото опционально. Если не прикреплено — `imagePath` и `thumbnailPath` = `null`.

---

## 6. Выбор магазина

- Combobox: текстовое поле с автодополнением из истории + возможность ввести новое название
- Никаких предзаданных значений
- Таблица `stores` в Room наполняется пользователем
- Новый магазин автоматически добавляется при первом использовании

---

## 7. Геолокация

**Компонент:** `LocationProvider` — отдельный класс, инжектируется в ViewModel. Не является частью `ImageAnalyzer`.

```kotlin
class LocationProvider(private val context: Context) {
    suspend fun getCurrentLocation(): Location?
}
```

- Разрешение: `ACCESS_FINE_LOCATION` (точность важна для ТЦ с несколькими магазинами)
- Если пользователь отказал — возвращает `null`, приложение работает без координат
- Координаты запрашиваются в ViewModel параллельно с OCR и сохраняются в Room вместе с результатом сканирования
- **Поток запроса разрешения:** при первом сканировании с камеры → rationale-диалог → системный запрос. При отказе — больше не спрашиваем, координаты = `null`

---

## 8. Производительность и устойчивость

### Фоновая обработка

```
UI Thread              Background (Dispatchers.IO)
──────────             ──────────────────────────
Нажал "снять" →        1. Сохранить фото в filesDir
Показать спиннер       2. ScanRepository.createProcessing() → scanId
                       3. OCR + Barcode + Location (параллельно)
                       4. Парсинг (~мс)
                       5. Генерация thumbnail
← Показать карточку    6. ScanRepository.markCompleted()
```

### Устойчивость к крашам

Принцип: **сохраняй как можно раньше**.

1. Фото сохранено в filesDir → `ScanRepository.createProcessing()` создаёт запись (`PROCESSING`)
2. Если краш → при запуске: `ScanRepository.getProcessingScans()` → повторить OCR для каждой
3. OCR завершён → `ScanRepository.markCompleted()`

### Оптимизация для слабых устройств

- Bitmap: даунсемплинг через `inSampleSize` (короткая сторона >= 1080px)
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
FusedLocationProviderClient — геолокация (Google Play Services)
```

---

## 11. Что НЕ входит в эту подсистему (следующие этапы)

- Полная Room-схема и DAO (подсистема «БД + История»). Минимальный `Scan` и `ScanRepository` определены здесь для crash-recovery
- UI экранов (подсистема «UI»)
- Сравнение товаров по штрихкоду (подсистема «БД + История»)
- Серверная обработка и подписка (будущий релиз). `SyncStatus` будет расширен через Room-миграцию
- Автоопределение магазина по координатам (будущий релиз)
