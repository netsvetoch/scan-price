package ru.ainetico.honestprice.ui.history

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ainetico.honestprice.data.Scan
import ru.ainetico.honestprice.model.WeightUnit

@Composable
fun ScanCard(scan: Scan, onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Top
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(scan.productName ?: "—", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(4.dp))
                if (!scan.storeName.isNullOrBlank()) {
                    Text(scan.storeName, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.height(2.dp))
                val priceText = buildString {
                    scan.priceRegular?.let { append("$it ₽") }
                    scan.priceDiscount?.let { append(" → $it ₽") }
                    scan.weightValue?.let { w ->
                        val unitName = scan.weightUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }?.displayName ?: ""
                        append(" / $w $unitName")
                    }
                }
                if (priceText.isNotBlank()) {
                    Text(priceText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            val honestPrice = scan.pricePerUnitDiscount ?: scan.pricePerUnit
            val unitName = scan.displayUnit?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }?.displayName ?: ""
            if (honestPrice != null) {
                Text("$honestPrice ₽/$unitName", color = Color(0xFF2E7D32), fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        }
    }
}
