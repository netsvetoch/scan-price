package ru.ainetico.honestprice.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ainetico.honestprice.data.Scan
import ru.ainetico.honestprice.ui.theme.PriceGreen
import ru.ainetico.honestprice.model.WeightUnit
import java.math.BigDecimal
import java.math.MathContext

@Composable
fun ScanCard(scan: Scan, onClick: () -> Unit) {
  Card(
    modifier = Modifier
      .fillMaxWidth()
      .clickable(onClick = onClick),
  ) {
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(16.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.Top
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          scan.productName ?: "—",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(4.dp))
        if (!scan.storeName.isNullOrBlank()) {
          Text(
            scan.storeName,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
        Spacer(Modifier.height(2.dp))
        val priceText = remember(
          scan.priceRegular, scan.priceDiscount, scan.weightValue, scan.weightUnit
        ) {
          buildString {
            scan.priceRegular?.let { append("$it ₽") }
            scan.priceDiscount?.let { append(" → $it ₽") }
            scan.weightValue?.let { w ->
              val unitName =
                scan.weightUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }?.displayName
                  ?: ""
              append(" / $w $unitName")
            }
          }
        }
        if (priceText.isNotBlank()) {
          Text(
            priceText,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
          )
        }
      }
      val honestPrice = remember(scan.pricePerUnitDiscount, scan.pricePerUnit) {
        (scan.pricePerUnitDiscount ?: scan.pricePerUnit)
          ?.let { BigDecimal(it).round(MathContext(3)).stripTrailingZeros().toPlainString() }
      }
      val unitName = remember(scan.displayUnit) {
        scan.displayUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }?.displayName
          ?: ""
      }
      if (honestPrice != null) {
        Text(
          "$honestPrice ₽/$unitName",
          color = PriceGreen,
          fontWeight = FontWeight.Bold,
          fontSize = 18.sp
        )
      }
    }
  }
}
