package ru.ainetico.honestprice.ui.result

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.model.WeightUnit

@Composable
fun ResultScreen(
    viewModel: ResultViewModel,
    onSaved: () -> Unit
) {
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.storeSuggestions.collectAsState()
    val event by viewModel.event.collectAsState()

    LaunchedEffect(event) {
        if (event is ResultEvent.Saved) {
            onSaved()
            viewModel.eventConsumed()
        }
    }

    Scaffold { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentAlignment = androidx.compose.ui.Alignment.BottomCenter
        ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {

            // Photo preview
            if (state.imagePath != null) {
                val bitmap = remember(state.imagePath) {
                    BitmapFactory.decodeFile(state.imagePath)
                }
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(120.dp)
                            .clip(RoundedCornerShape(12.dp)),
                        contentScale = ContentScale.Crop
                    )
                }
            }

            // Product name
            OutlinedTextField(
                value = state.productName,
                onValueChange = { viewModel.updateProductName(it) },
                label = { Text(stringResource(R.string.result_product_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true
            )

            // Prices row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.priceRegular,
                    onValueChange = { viewModel.updatePriceRegular(it) },
                    label = { Text(stringResource(R.string.result_price_regular)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text("₽") }
                )
                OutlinedTextField(
                    value = state.priceDiscount,
                    onValueChange = { viewModel.updatePriceDiscount(it) },
                    label = { Text(stringResource(R.string.result_price_discount)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text("₽") }
                )
            }

            // Weight + unit
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = state.weightValue,
                    onValueChange = { viewModel.updateWeightValue(it) },
                    label = { Text(stringResource(R.string.result_weight)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true
                )
            }

            // Unit selector - Segmented Button
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                state.availableUnits.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = state.weightUnit == unit,
                        onClick = { viewModel.selectUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = state.availableUnits.size
                        )
                    ) {
                        Text(unit.displayName)
                    }
                }
            }

            // Store
            StoreComboBox(
                value = state.storeName,
                suggestions = suggestions,
                onValueChange = { viewModel.updateStoreName(it) }
            )

            // Barcode
            OutlinedTextField(
                value = state.barcode,
                onValueChange = { viewModel.updateBarcode(it) },
                label = { Text(stringResource(R.string.result_barcode)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = state.barcode.isNotBlank() && !state.isManualEntry
            )

            // Price card
            PriceCard(
                pricePerUnit = state.pricePerUnit,
                pricePerUnitDiscount = state.pricePerUnitDiscount,
                displayUnit = state.displayUnit
            )

            // Save button
            Button(
                onClick = { viewModel.save() },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                enabled = !state.isSaving
            ) {
                Text(
                    if (state.isSaving) stringResource(R.string.result_saving)
                    else stringResource(R.string.result_save)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
        }
    }
}
