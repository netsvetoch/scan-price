package ru.ainetico.honestprice.ui.settings

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.appcompat.app.AppCompatDelegate
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.core.os.LocaleListCompat
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.ui.theme.Dimens

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LanguageSection() {
  val context = LocalContext.current
  val currentLang = AppCompatDelegate.getApplicationLocales().get(0)?.language
    ?: context.resources.configuration.locales[0].language
  val currentLangName = when (currentLang) {
    "en" -> "English"
    else -> "Русский"
  }

  if (Build.VERSION.SDK_INT >= 33) {
    Row(
      modifier = Modifier
          .fillMaxWidth()
          .clickable {
              val intent = Intent(Settings.ACTION_APP_LOCALE_SETTINGS)
              intent.data = Uri.parse("package:${context.packageName}")
              context.startActivity(intent)
          },
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.settings_language_title),
        style = MaterialTheme.typography.titleMedium
      )
      Text(
        text = "$currentLangName ›",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary
      )
    }
  } else {
    var selectedLanguage by remember { mutableStateOf(currentLang) }
    var languageExpanded by remember { mutableStateOf(false) }
    val languageOptions = listOf("ru" to "Русский", "en" to "English")

    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically
    ) {
      Text(
        text = stringResource(R.string.settings_language_title),
        style = MaterialTheme.typography.titleMedium
      )
      ExposedDropdownMenuBox(
        expanded = languageExpanded,
        onExpandedChange = { languageExpanded = it },
        modifier = Modifier.width(Dimens.LanguageDropdownWidth)
      ) {
        OutlinedTextField(
          value = languageOptions.first { it.first == selectedLanguage }.second,
          onValueChange = {},
          readOnly = true,
          singleLine = true,
          modifier = Modifier.menuAnchor(),
          trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = languageExpanded) }
        )
        ExposedDropdownMenu(
          expanded = languageExpanded,
          onDismissRequest = { languageExpanded = false }
        ) {
          languageOptions.forEach { (tag, name) ->
            DropdownMenuItem(
              text = { Text(name) },
              onClick = {
                selectedLanguage = tag
                languageExpanded = false
                AppCompatDelegate.setApplicationLocales(
                  LocaleListCompat.forLanguageTags(tag)
                )
              }
            )
          }
        }
      }
    }
  }
}
