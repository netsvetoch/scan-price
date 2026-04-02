package ru.ainetico.honestprice.ui.settings

import androidx.annotation.StringRes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource

@Composable
fun SettingsSectionHeader(
    @StringRes titleRes: Int,
    @StringRes descriptionRes: Int? = null
) {
    Text(
        text = stringResource(titleRes),
        style = MaterialTheme.typography.titleMedium
    )
    if (descriptionRes != null) {
        Text(
            text = stringResource(descriptionRes),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
