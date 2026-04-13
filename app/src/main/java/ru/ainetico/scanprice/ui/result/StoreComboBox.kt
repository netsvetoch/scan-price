package ru.ainetico.scanprice.ui.result

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import ru.ainetico.scanprice.R
import ru.ainetico.scanprice.data.Store

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StoreComboBox(
  value: String,
  suggestions: List<Store>,
  onValueChange: (String) -> Unit,
  modifier: Modifier = Modifier
) {
  var expanded by remember { mutableStateOf(false) }

  ExposedDropdownMenuBox(
    expanded = expanded,
    onExpandedChange = { expanded = it },
    modifier = modifier
  ) {
    OutlinedTextField(
      value = value,
      onValueChange = {
        onValueChange(it)
        expanded = true
      },
      label = { Text(stringResource(R.string.result_store)) },
      modifier = Modifier
          .fillMaxWidth()
          .menuAnchor(),
      singleLine = true,
      trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) }
    )

    if (suggestions.isNotEmpty()) {
      ExposedDropdownMenu(
        expanded = expanded,
        onDismissRequest = { expanded = false }
      ) {
        suggestions.forEach { store ->
          DropdownMenuItem(
            text = { Text(store.name) },
            onClick = {
              onValueChange(store.name)
              expanded = false
            }
          )
        }
      }
    }
  }
}
