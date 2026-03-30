# ЧестнаяЦена — UI + Интеграция: Спецификация подсистемы

**Дата:** 2026-03-30
**Подсистема:** Onboarding, экраны камеры/результата/истории, навигация, интеграция с OCR+Парсер
**Статус:** MVP / offline-first / open-source
**Зависимости:** подсистема OCR+Парсер (реализована)
**Изменения в подсистеме 1:** эта спецификация требует добавления `getAllScansFlow()` в `ScanDao`, `updateUserFields()` и `createManual()` в `ScanRepository`. Метод `ImagePreprocessor` в реализации называется `processBitmap()` (не `process()` как в спеке подсистемы 1) — UI вызывает `ImageAnalyzer.analyze()` который использует правильное имя.

---

## 1. Обзор

Вторая подсистема приложения «ЧестнаяЦена». Реализует пользовательский интерфейс: onboarding, экран камеры с захватом изображений, экран результата с редактированием полей и вычислением «честной цены», экран истории сканирований. Связывает UI с уже реализованным Data Layer (ImageAnalyzer, ScanRepository, LocationProvider).

### Принципы

- **Single Activity + Compose Navigation** — один Activity, навигация через NavHost
- **MVVM** — каждый экран со своим ViewModel, UI реагирует на State
- **Мгновенная отзывчивость** — вся тяжёлая работа в фоне, UI никогда не блокируется
- **i18n-ready** — все строки через `strings.xml`
- **Material 3** — следуем гайдлайнам Material Design 3

---

## 2. Навигация

### Граф маршрутов NavHost

```
Onboarding → Camera → Result(scanId) → History
                                          ↓
                              FAB: Camera / Gallery / Manual
                                          ↓
                                Camera или Result(пустой)
```

### Маршруты

```kotlin
sealed class Screen(val route: String) {
    object Onboarding : Screen("onboarding")
    object Camera : Screen("camera?openGallery={openGallery}") {
        fun createRoute(openGallery: Boolean = false) = "camera?openGallery=$openGallery"
    }
    object Result : Screen("result/{scanId}") {
        fun createRoute(scanId: Long) = "result/$scanId"
    }
    object ResultManual : Screen("result_manual")
    object History : Screen("history")
}
```

### Логика старта

При запуске `MainActivity` проверяет SharedPreferences:
- `onboarding_completed == false` → навигация на Onboarding
- `onboarding_completed == true` и есть записи в History → навигация на History
- `onboarding_completed == true` и History пуст → навигация на Camera

### Back-stack

- Onboarding → Camera: Onboarding убирается из стека (`popUpTo("onboarding") { inclusive = true }`)
- Camera → Result: Camera остаётся в стеке
- Result → History: Result и Camera убираются (`popUpTo("camera") { inclusive = true }`)
- History → Camera (FAB): Camera добавляется поверх History
- History → ResultManual (FAB ручной ввод): Result добавляется поверх History
- History → Camera(openGallery=true) (FAB галерея): Camera открывается и сразу запускает gallery picker

---

## 3. Onboarding

### OnboardingScreen

`HorizontalPager` с двумя страницами и индикатором (dots).

**Страница 1 — Камера:**
- Иконка камеры (Material Icon)
- Заголовок: «Сканируйте ценник»
- Текст: «Наведите камеру на ценник — мы рассчитаем честную цену за кг или литр»
- Подсказка: «Также можно загрузить фото из галереи или ввести данные вручную»
- Кнопка «Далее»

**Страница 2 — Геолокация:**
- Иконка локации (Material Icon)
- Заголовок: «Сохраняйте местоположение»
- Текст: «Это поможет запоминать магазин и добавить новые функции в будущем»
- Кнопка «Разрешить» → запрос `ACCESS_FINE_LOCATION` через `rememberLauncherForActivityResult(RequestPermission)`
- Текстовая кнопка «Пропустить»

**Каноничный поток разрешения геолокации:** запрос происходит ТОЛЬКО на onboarding. Если пользователь пропустил — `LocationProvider.getCurrentLocation()` вернёт `null` при сохранении, координаты не записываются. Повторный запрос не делается. Это поведение заменяет описание в спеке подсистемы 1 (секция 7, «при первом сканировании»).

**При завершении:**
- Сохраняем `onboarding_completed = true` в SharedPreferences
- Навигация на Camera с очисткой back-stack

---

## 4. Экран камеры (CameraScreen)

### CameraViewModel

```kotlin
sealed class CameraState {
    object Preview : CameraState()
    data class Scanning(val previewBitmap: Bitmap) : CameraState()
}

class CameraViewModel(
    private val imageAnalyzer: ImageAnalyzer,
    private val scanRepository: ScanRepository
) : ViewModel() {
    val state: StateFlow<CameraState>

    fun capture(bitmap: Bitmap, cropRect: Rect)
    fun importFromGallery(uri: Uri, context: Context)
    fun retake()  // отмена OCR, возврат к Preview
}
```

### UI компоненты

**Состояние Preview:**
- CameraX `PreviewView` через `AndroidView` в Compose
- Затемнённая область за пределами рамки (горизонтальный прямоугольник, ~70% ширины, по центру)
- Рамка: скруглённые углы, тонкая белая обводка
- Нижняя панель с тремя кнопками:
  - Слева: кнопка «Галерея» (иконка, круглая)
  - Центр: кнопка «Снять» (большая белая круглая, как в стандартной камере)
  - Справа: кнопка «Вручную» (иконка, круглая)

**Состояние Scanning:**
- Превью замораживается (показываем сохранённый Bitmap)
- Градиентная анимация «сканирования» — полупрозрачная полоса, двигающаяся сверху вниз по рамке
- Кнопка «Переснять» по центру нижней панели (заменяет три кнопки)

### Camera2 настройки (через CameraX Interop)

- `CONTROL_AE_MODE_ON` с компенсацией экспозиции
- `CONTROL_AWB_MODE_FLUORESCENT`
- `CONTROL_AF_MODE_CONTINUOUS_PICTURE`
- `NOISE_REDUCTION_MODE_HIGH_QUALITY`

### Поток обработки

1. Пользователь нажимает «Снять» → `CameraViewModel.capture(bitmap, cropRect)`
2. State → `Scanning(previewBitmap)` — UI показывает анимацию
3. ViewModel в фоне:
   a. Сохраняет фото в `filesDir/images/originals/{scanId}_{timestamp}.jpg`
   b. `ScanRepository.createProcessing(imagePath)` → получает `scanId`
   c. `ImageAnalyzer.analyze(bitmap, cropRect)` → получает `AnalysisResult`
   d. `ScanRepository.markCompleted(scanId, tag, price)`
   e. Генерирует thumbnail в `filesDir/images/thumbnails/`
4. Навигация на `Result(scanId)`

### Галерея

- `ActivityResultContracts.GetContent("image/*")` → получаем `content://` URI
- Копируем в `filesDir/images/originals/`
- Тот же Scanning flow

### Ручной ввод

- Навигация напрямую на `ResultManual` — без `scanId`, все поля пустые

### Разрешение камеры

- Запрос `Manifest.permission.CAMERA` при первом показе CameraScreen
- Если отказано — показываем placeholder с текстом «Для сканирования нужен доступ к камере» и кнопкой «Открыть настройки»

---

## 5. Экран результата (ResultScreen)

### ResultViewModel

```kotlin
data class ResultState(
    val productName: String = "",
    val priceRegular: String = "",
    val priceDiscount: String = "",
    val weightValue: String = "",
    val weightUnit: WeightUnit = WeightUnit.PCS,
    val availableUnits: List<WeightUnit> = listOf(WeightUnit.PCS),
    val storeName: String = "",
    val barcode: String = "",
    val imagePath: String? = null,
    val pricePerUnit: String = "",
    val pricePerUnitDiscount: String = "",
    val displayUnit: WeightUnit = WeightUnit.PCS,
    val isManualEntry: Boolean = false,
    val isSaving: Boolean = false
)

class ResultViewModel(
    private val scanRepository: ScanRepository,
    private val storeDao: StoreDao,
    private val locationProvider: LocationProvider,
    private val calculator: PriceCalculator
) : ViewModel() {
    val state: StateFlow<ResultState>
    val storesSuggestions: StateFlow<List<Store>>

    fun loadScan(scanId: Long)       // загрузить из Room
    fun updateField(field, value)     // обновить любое поле → пересчёт честной цены
    fun selectUnit(unit: WeightUnit)  // переключить единицу → пересчёт
    fun searchStores(query: String)   // автодополнение магазинов
    fun save()                        // сохранить + запросить локацию + навигация
}
```

### UI компоненты

Сверху вниз (scrollable `Column`):

1. **Превью фото** — если есть `imagePath`, показываем thumbnail с скруглёнными углами. Если ручной ввод — не показываем.

2. **Название товара** — `OutlinedTextField`, одна строка

3. **Цена обычная + Цена по скидке** — два `OutlinedTextField` в `Row`, тип клавиатуры `KeyboardType.Decimal`

4. **Вес/объём + Единица** — `OutlinedTextField` (число) + `SingleChoiceSegmentedButtonRow`:
   - Если распознано г/кг → показываем [г, кг]
   - Если распознано мл/л → показываем [мл, л]
   - Если шт или не определено → показываем [г, кг, мл, л, шт]
   - Переключение → мгновенный пересчёт честной цены

5. **Магазин** — `ExposedDropdownMenuBox`:
   - Ввод текста → фильтрация из `StoreDao.search(query)`
   - Выбор из списка или ввод нового
   - Новый магазин автоматически добавляется в `stores` при сохранении

6. **Штрихкод** — `OutlinedTextField`, readonly если распознан (серый текст), editable если пустой

7. **Карточка «Честная цена»** — `Card` с зелёным градиентом:
   - Крупно: честная цена за единицу (скидочная, если есть)
   - Мельче: обычная честная цена (если есть скидка)
   - Единица измерения
   - Пересчитывается мгновенно при изменении любого поля (цена, вес, единица)

8. **Кнопка «Сохранить»** — `Button` на всю ширину, Material 3 filled

### Пересчёт честной цены

При каждом изменении полей (цена, вес, единица):
1. Собираем `ParsedPriceTag` из текущих значений UI
2. `PriceCalculator.calculate(tag, targetUnit)` → `PriceResult`
3. Обновляем карточку честной цены

### Сохранение

1. Запрашиваем геолокацию: `LocationProvider.getCurrentLocation()` (если есть разрешение, иначе `null`)
2. Если новый магазин — `StoreDao.insert(Store(name = storeName))`
3. Для ручного ввода: `ScanRepository.createManual()` → создаёт запись без фото (`status = COMPLETED`)
4. `ScanRepository.updateUserFields(scanId, tag, price, storeName, latitude, longitude)` — сохраняет все поля включая магазин и координаты
5. Навигация на History

**Изменение в ScanRepository (подсистема 1):** добавить методы:

```kotlin
interface ScanRepository {
    // ... существующие методы ...
    suspend fun createManual(): Long   // создаёт запись без imagePath, status=COMPLETED
    suspend fun updateUserFields(
        scanId: Long,
        tag: ParsedPriceTag,
        price: PriceResult?,
        storeName: String?,
        latitude: Double?,
        longitude: Double?
    )
}
```

`updateUserFields` заменяет `markCompleted` на экране результата — он записывает все поля включая `storeName`, `latitude`, `longitude`, которых нет в `ParsedPriceTag`.

---

## 6. Экран истории (HistoryScreen)

### HistoryViewModel

```kotlin
class HistoryViewModel(
    private val scanDao: ScanDao
) : ViewModel() {
    val scans: StateFlow<List<Scan>>  // из scanDao.getAllScansFlow()
}
```

**Изменение в ScanDao (подсистема 1):** добавить реактивный метод:

```kotlin
@Query("SELECT * FROM scans WHERE status != 'PROCESSING' ORDER BY createdAt DESC")
fun getAllScansFlow(): Flow<List<Scan>>
```

Существующий `suspend fun getAllScans()` сохраняется для разовых запросов. `getAllScansFlow()` — реактивная подписка для `HistoryViewModel`.
```

### UI компоненты

- `LazyColumn` — список карточек сканирований, новые сверху
- Каждая карточка (`Card`):
  - Название товара (bold)
  - Магазин (серый текст)
  - Обычная цена → скидочная цена
  - Честная цена за единицу (зелёный, крупный, справа)
- Клик по карточке → навигация на `Result(scanId)` для просмотра/редактирования. При повторном сохранении ранее сохранённого скана — `status` обновляется до `EDITED`
- Пустое состояние: текст «Нет сканирований» + подсказка «Нажмите 📷 чтобы начать»

### FAB-кнопки

Три кнопки в правом нижнем углу, вертикальный стек снизу вверх:
1. **Камера** (главный, `LargeFloatingActionButton`) — навигация на `Camera(openGallery=false)`
2. **Галерея** (`SmallFloatingActionButton`) — навигация на `Camera(openGallery=true)`. `CameraScreen` при `openGallery=true` сразу запускает gallery picker через `LaunchedEffect`
3. **Ручной ввод** (`SmallFloatingActionButton`) — навигация на `ResultManual`

---

## 7. Зависимости (новые)

```
Compose Navigation    — навигация между экранами
Compose Foundation    — HorizontalPager для Onboarding
CameraX              — уже добавлена в подсистеме 1
```

---

## 8. Файловая структура

```
app/src/main/java/ru/ainetico/honestprice/
├── MainActivity.kt                          — NavHost, startDestination logic
├── navigation/
│   └── Screen.kt                            — sealed class с маршрутами
├── ui/
│   ├── onboarding/
│   │   └── OnboardingScreen.kt              — HorizontalPager, 2 страницы
│   ├── camera/
│   │   ├── CameraScreen.kt                  — CameraX preview + рамка + кнопки
│   │   ├── CameraViewModel.kt               — capture, gallery, scanning state
│   │   └── ScanningOverlay.kt               — градиентная анимация сканирования
│   ├── result/
│   │   ├── ResultScreen.kt                  — форма + карточка честной цены
│   │   ├── ResultViewModel.kt               — поля, пересчёт, сохранение
│   │   ├── PriceCard.kt                     — зелёная карточка честной цены
│   │   └── StoreComboBox.kt                 — ExposedDropdownMenuBox для магазина
│   ├── history/
│   │   ├── HistoryScreen.kt                 — LazyColumn + FAB
│   │   ├── HistoryViewModel.kt              — Flow подписка на Room
│   │   └── ScanCard.kt                      — карточка одного сканирования
│   └── theme/                               — уже существует
├── model/                                   — уже реализовано ✅
├── parser/                                  — уже реализовано ✅
├── calculator/                              — уже реализовано ✅
├── ocr/                                     — уже реализовано ✅
├── image/                                   — уже реализовано ✅
├── analyzer/                                — уже реализовано ✅
├── data/                                    — уже реализовано ✅
└── location/                                — уже реализовано ✅
```

---

## 9. Что НЕ входит в эту подсистему

- Поиск/фильтрация в истории
- Сравнение товаров по штрихкоду
- Удаление сканирований
- Серверная синхронизация
- Тёмная тема (используем системную по умолчанию через Material 3)
