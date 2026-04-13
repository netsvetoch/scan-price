package ru.ainetico.honestprice.widget

import android.content.Context
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import dagger.hilt.EntryPoint
import dagger.hilt.EntryPoints
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ru.ainetico.honestprice.MainActivity
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.data.ScanDao
import ru.ainetico.honestprice.model.WeightUnit
import ru.ainetico.honestprice.util.formatRelativeDate
import java.math.BigDecimal
import java.math.MathContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class LastScanWidget : GlanceAppWidget() {

  @EntryPoint
  @InstallIn(SingletonComponent::class)
  interface WidgetEntryPoint {
    fun scanDao(): ScanDao
  }

  override suspend fun provideGlance(context: Context, id: GlanceId) {
    val lastScan = withContext(Dispatchers.IO) {
      val entryPoint =
        EntryPoints.get(context.applicationContext, WidgetEntryPoint::class.java)
      entryPoint.scanDao().getAllScans().firstOrNull()
    }

    provideContent {
      GlanceTheme {
        Box(
          modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(actionStartActivity<MainActivity>())
            .padding(16.dp),
          contentAlignment = Alignment.CenterStart
        ) {
          if (lastScan == null) {
            Column(
              modifier = GlanceModifier.fillMaxWidth(),
              horizontalAlignment = Alignment.CenterHorizontally
            ) {
              Text(
                text = context.getString(R.string.app_name),
                style = TextStyle(
                  fontWeight = FontWeight.Bold,
                  color = GlanceTheme.colors.onSurface
                )
              )
              Spacer(modifier = GlanceModifier.height(4.dp))
              Text(
                text = context.getString(R.string.history_empty),
                style = TextStyle(
                  color = GlanceTheme.colors.secondary
                )
              )
            }
          } else {
            Row(
              modifier = GlanceModifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {
              Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                  text = lastScan.productName ?: "—",
                  style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = GlanceTheme.colors.onSurface
                  ),
                  maxLines = 1
                )
                Spacer(modifier = GlanceModifier.height(2.dp))

                val priceText = buildString {
                  lastScan.priceRegular?.let { append("$it ₽") }
                  lastScan.priceDiscount?.let { append(" → $it ₽") }
                  lastScan.weightValue?.let { w ->
                    val unitName = lastScan.weightUnit
                      ?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
                      ?.displayName ?: ""
                    append(" / $w $unitName")
                  }
                }
                if (priceText.isNotBlank()) {
                  Text(
                    text = priceText,
                    style = TextStyle(color = GlanceTheme.colors.secondary),
                    maxLines = 1
                  )
                }

                Spacer(modifier = GlanceModifier.height(2.dp))
                val relDate = formatRelativeDate(context, lastScan.createdAt)
                val time = SimpleDateFormat("HH:mm", Locale("ru"))
                  .format(Date(lastScan.createdAt))
                val dateStr = "$relDate, $time"
                val store = lastScan.storeName
                val subtitle = listOfNotNull(store, dateStr).joinToString(" · ")
                Text(
                  text = subtitle,
                  style = TextStyle(color = GlanceTheme.colors.secondary),
                  maxLines = 1
                )
              }

              val honestPrice =
                (lastScan.pricePerUnitDiscount ?: lastScan.pricePerUnit)
                  ?.let {
                    BigDecimal(it).round(MathContext(3)).stripTrailingZeros()
                      .toPlainString()
                  }
              val unitName = lastScan.displayUnit
                ?.let { runCatching { WeightUnit.valueOf(it) }.getOrNull() }
                ?.displayName ?: ""

              if (honestPrice != null) {
                Spacer(modifier = GlanceModifier.width(8.dp))
                Text(
                  text = "$honestPrice ₽/$unitName",
                  style = TextStyle(
                    fontWeight = FontWeight.Bold,
                    color = ColorProvider(
                      androidx.compose.ui.graphics.Color(
                        0xFF4CAF50
                      )
                    )
                  )
                )
              }
            }
          }
        }
      }
    }
  }
}

class LastScanWidgetReceiver : GlanceAppWidgetReceiver() {
  override val glanceAppWidget: GlanceAppWidget = LastScanWidget()
}
