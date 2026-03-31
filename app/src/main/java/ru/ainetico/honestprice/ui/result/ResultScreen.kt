package ru.ainetico.honestprice.ui.result

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.mutableStateOf
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
    onSaved: () -> Unit,
    onCancel: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val state by viewModel.state.collectAsState()
    val suggestions by viewModel.storeSuggestions.collectAsState()
    val event by viewModel.event.collectAsState()

    // Handle back button — cancel without saving
    androidx.activity.compose.BackHandler { onCancel() }

    LaunchedEffect(event) {
        if (event is ResultEvent.Saved) {
            onSaved()
            viewModel.eventConsumed()
        }
    }

    // Colors for auto-recognized fields
    val autoFilledColors = OutlinedTextFieldDefaults.colors(
        unfocusedBorderColor = MaterialTheme.colorScheme.primary,
        unfocusedLabelColor = MaterialTheme.colorScheme.primary
    )
    val defaultColors = OutlinedTextFieldDefaults.colors()

    fun colorsFor(value: String) = if (value.isNotBlank() && !state.isManualEntry) autoFilledColors else defaultColors

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(8.dp))

            // Photo preview — async loading with skeleton
            if (state.imagePath != null) {
                var bitmap by remember { mutableStateOf<android.graphics.Bitmap?>(null) }
                LaunchedEffect(state.imagePath) {
                    bitmap = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
                        BitmapFactory.decodeFile(state.imagePath)
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(120.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap!!.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    }
                }
            }

            // Product name
            OutlinedTextField(
                value = state.productName,
                onValueChange = { viewModel.updateProductName(it) },
                label = { Text(stringResource(R.string.result_product_name)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                colors = colorsFor(state.productName)
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
                    suffix = { Text("₽") },
                    colors = colorsFor(state.priceRegular)
                )
                OutlinedTextField(
                    value = state.priceDiscount,
                    onValueChange = { viewModel.updatePriceDiscount(it) },
                    label = { Text(stringResource(R.string.result_price_discount)) },
                    modifier = Modifier.weight(1f),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    suffix = { Text("₽") },
                    colors = colorsFor(state.priceDiscount)
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
                    singleLine = true,
                    colors = colorsFor(state.weightValue)
                )
            }

            // Unit selector - Segmented Button
            val unitAutoFilled = state.weightValue.isNotBlank() && !state.isManualEntry
            val unitBorder = if (unitAutoFilled) {
                SegmentedButtonDefaults.borderStroke(
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                SegmentedButtonDefaults.borderStroke(
                    color = MaterialTheme.colorScheme.outline
                )
            }
            SingleChoiceSegmentedButtonRow(modifier = Modifier.fillMaxWidth()) {
                state.availableUnits.forEachIndexed { index, unit ->
                    SegmentedButton(
                        selected = state.weightUnit == unit,
                        onClick = { viewModel.selectUnit(unit) },
                        shape = SegmentedButtonDefaults.itemShape(
                            index = index,
                            count = state.availableUnits.size
                        ),
                        border = unitBorder
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

            // TODO: Barcode — add separate barcode scanner later
            /*OutlinedTextField(
                value = state.barcode,
                onValueChange = { viewModel.updateBarcode(it) },
                label = { Text(stringResource(R.string.result_barcode)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                readOnly = state.barcode.isNotBlank() && !state.isManualEntry
            )*/

            // Price card
            PriceCard(
                pricePerUnit = state.pricePerUnit,
                pricePerUnitDiscount = state.pricePerUnitDiscount,
                displayUnit = state.displayUnit
            )

            // Action buttons
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.result_cancel))
                }
                Button(
                    onClick = { viewModel.save() },
                    modifier = Modifier.weight(1f),
                    enabled = !state.isSaving
                ) {
                    Text(
                        if (state.isSaving) stringResource(R.string.result_saving)
                        else stringResource(R.string.result_save)
                    )
                }
            }

            // Delete button — only for existing scans from history
            if (onDelete != null) {
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) {
                    Text(stringResource(R.string.result_delete))
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }
}
