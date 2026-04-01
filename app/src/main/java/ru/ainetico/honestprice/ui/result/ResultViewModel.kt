package ru.ainetico.honestprice.ui.result

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.calculator.PriceCalculator
import ru.ainetico.honestprice.data.ScanRepository
import ru.ainetico.honestprice.data.Store
import ru.ainetico.honestprice.data.StoreDao
import ru.ainetico.honestprice.location.LocationProvider
import ru.ainetico.honestprice.model.ParsedPriceTag
import ru.ainetico.honestprice.model.WeightUnit
import java.math.BigDecimal

data class ResultState(
  val scanId: Long? = null,
  val productName: String = "",
  val productDescription: String = "",
  val priceRegular: String = "",
  val priceDiscount: String = "",
  val weightValue: String = "",
  val weightUnit: WeightUnit = WeightUnit.PCS,
  val availableUnits: List<WeightUnit> = listOf(
    WeightUnit.G,
    WeightUnit.KG,
    WeightUnit.ML,
    WeightUnit.L,
    WeightUnit.PCS
  ),
  val storeName: String = "",
  val barcode: String = "",
  val imagePath: String? = null,
  val pricePerUnit: String = "",
  val pricePerUnitDiscount: String = "",
  val displayUnit: WeightUnit = WeightUnit.PCS,
  val isManualEntry: Boolean = false,
  val isSaving: Boolean = false
)

sealed class ResultEvent {
  object Saved : ResultEvent()
}

@dagger.hilt.android.lifecycle.HiltViewModel
class ResultViewModel @javax.inject.Inject constructor(
  private val scanRepository: ScanRepository,
  private val storeDao: StoreDao,
  private val locationProvider: LocationProvider,
  private val calculator: PriceCalculator
) : ViewModel() {

  private val _state = MutableStateFlow(ResultState())
  val state: StateFlow<ResultState> = _state

  private val _storeSuggestions = MutableStateFlow<List<Store>>(emptyList())
  val storeSuggestions: StateFlow<List<Store>> = _storeSuggestions

  private val _event = Channel<ResultEvent>(Channel.BUFFERED)
  val event = _event.receiveAsFlow()

  fun loadScan(scan: ru.ainetico.honestprice.data.Scan) {
    val unit = scan.weightUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
    val availableUnits =
      listOf(WeightUnit.G, WeightUnit.KG, WeightUnit.ML, WeightUnit.L, WeightUnit.PCS)
    _state.value = ResultState(
      scanId = scan.id,
      productName = scan.productName ?: "",
      productDescription = scan.productDescription ?: "",
      priceRegular = scan.priceRegular ?: "",
      priceDiscount = scan.priceDiscount ?: "",
      weightValue = scan.weightValue ?: "",
      weightUnit = unit ?: WeightUnit.PCS,
      availableUnits = availableUnits,
      storeName = scan.storeName ?: "",
      barcode = scan.barcode ?: "",
      imagePath = scan.imagePath,
      isManualEntry = false
    )
    recalculatePrice()
  }

  fun loadFromAnalysis(
    scanId: Long,
    result: ru.ainetico.honestprice.model.AnalysisResult,
    imagePath: String?
  ) {
    val tag = result.tag
    val unit = tag.weightUnit
    val availableUnits =
      listOf(WeightUnit.G, WeightUnit.KG, WeightUnit.ML, WeightUnit.L, WeightUnit.PCS)
    _state.value = ResultState(
      scanId = scanId,
      productName = tag.productName ?: "",
      productDescription = tag.productDescription ?: "",
      priceRegular = tag.priceRegular?.toPlainString() ?: "",
      priceDiscount = tag.priceDiscount?.toPlainString() ?: "",
      weightValue = tag.weightValue?.toPlainString() ?: "",
      weightUnit = unit ?: WeightUnit.PCS,
      availableUnits = availableUnits,
      barcode = tag.barcode ?: "",
      imagePath = imagePath,
      isManualEntry = false
    )
    recalculatePrice()
  }

  fun loadManual() {
    _state.value = ResultState(isManualEntry = true)
  }

  fun updateImagePath(path: String) {
    _state.update { it.copy(imagePath = path) }
  }

  fun updateProductName(value: String) {
    _state.update { it.copy(productName = value) }
  }

  fun updateProductDescription(value: String) {
    _state.update { it.copy(productDescription = value) }
  }

  fun updatePriceRegular(value: String) {
    _state.update { it.copy(priceRegular = value) }
    recalculatePrice()
  }

  fun updatePriceDiscount(value: String) {
    _state.update { it.copy(priceDiscount = value) }
    recalculatePrice()
  }

  fun updateWeightValue(value: String) {
    _state.update { it.copy(weightValue = value) }
    recalculatePrice()
  }

  fun selectUnit(unit: WeightUnit) {
    _state.update { it.copy(weightUnit = unit) }
    recalculatePrice()
  }

  fun updateStoreName(value: String) {
    _state.update { it.copy(storeName = value) }
    searchStores(value)
  }

  fun updateBarcode(value: String) {
    _state.update { it.copy(barcode = value) }
  }


  private fun searchStores(query: String) {
    viewModelScope.launch {
      _storeSuggestions.value = if (query.isBlank()) {
        storeDao.getAllStores()
      } else {
        storeDao.search(query)
      }
    }
  }

  private fun recalculatePrice() {
    val s = _state.value
    val regular = s.priceRegular.toBigDecimalOrNull()
    val discount = s.priceDiscount.toBigDecimalOrNull()
    val weight = s.weightValue.toBigDecimalOrNull()

    val tag = ParsedPriceTag(
      priceRegular = regular,
      priceDiscount = discount,
      weightValue = weight,
      weightUnit = s.weightUnit
    )
    val result = calculator.calculate(tag)
    _state.update {
      it.copy(
        pricePerUnit = result?.pricePerUnit?.round(java.math.MathContext(3))
          ?.stripTrailingZeros()?.toPlainString() ?: "",
        pricePerUnitDiscount = result?.pricePerUnitDiscount?.round(java.math.MathContext(3))
          ?.stripTrailingZeros()?.toPlainString() ?: "",
        displayUnit = result?.displayUnit ?: s.weightUnit
      )
    }
  }

  private fun String.toBigDecimalOrNull(): BigDecimal? {
    return try {
      val cleaned = this.replace(',', '.')
      if (cleaned.isBlank()) null else BigDecimal(cleaned)
    } catch (e: NumberFormatException) {
      null
    }
  }

  fun save() {
    val s = _state.value
    if (s.isSaving) return
    _state.update { it.copy(isSaving = true) }

    viewModelScope.launch {
      try {
        val scanId = s.scanId ?: withContext(Dispatchers.IO) {
          scanRepository.createManual()
        }

        val location = withContext(Dispatchers.IO) {
          locationProvider.getCurrentLocation()
        }

        if (s.storeName.isNotBlank()) {
          withContext(Dispatchers.IO) {
            storeDao.insert(Store(name = s.storeName))
          }
        }

        val tag = ParsedPriceTag(
          productName = s.productName.ifBlank { null },
          productDescription = s.productDescription.ifBlank { null },
          priceRegular = s.priceRegular.toBigDecimalOrNull(),
          priceDiscount = s.priceDiscount.toBigDecimalOrNull(),
          weightValue = s.weightValue.toBigDecimalOrNull(),
          weightUnit = s.weightUnit,
          barcode = s.barcode.ifBlank { null }
        )
        val price = calculator.calculate(tag)

        withContext(Dispatchers.IO) {
          scanRepository.updateUserFields(
            scanId = scanId,
            tag = tag,
            price = price,
            storeName = s.storeName.ifBlank { null },
            latitude = location?.latitude,
            longitude = location?.longitude
          )
        }

        _event.trySend(ResultEvent.Saved)
      } catch (e: Exception) {
        _state.update { it.copy(isSaving = false) }
      }
    }
  }
}
