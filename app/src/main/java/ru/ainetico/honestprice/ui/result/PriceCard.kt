package ru.ainetico.honestprice.ui.result

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.ui.theme.Dimens
import ru.ainetico.honestprice.ui.theme.PriceGreen
import ru.ainetico.honestprice.ui.theme.PriceGreenLight
import ru.ainetico.honestprice.model.WeightUnit

@Composable
fun PriceCard(
  pricePerUnit: String,
  pricePerUnitDiscount: String,
  displayUnit: WeightUnit,
  modifier: Modifier = Modifier
) {
  if (pricePerUnit.isBlank() && pricePerUnitDiscount.isBlank()) return

  val mainPrice = pricePerUnitDiscount.ifBlank { pricePerUnit }
  val showRegular = pricePerUnitDiscount.isNotBlank() && pricePerUnit.isNotBlank()

  Box(
    modifier = modifier
      .fillMaxWidth()
      .clip(RoundedCornerShape(Dimens.CornerRadiusLarge))
      .background(
        Brush.linearGradient(
          colors = listOf(PriceGreenLight, PriceGreen)
        )
      )
      .padding(Dimens.PriceCardPadding),
    contentAlignment = Alignment.Center
  ) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
      Text(
        text = stringResource(R.string.result_honest_price),
        color = Color.White.copy(alpha = 0.8f),
        fontSize = Dimens.FontSizeSubtitle
      )
      Spacer(modifier = Modifier.height(4.dp))
      Text(
        text = "$mainPrice ₽/${displayUnit.displayName}",
        color = Color.White,
        fontSize = Dimens.FontSizePrice,
        fontWeight = FontWeight.Bold
      )
      if (showRegular) {
        Spacer(modifier = Modifier.height(4.dp))
        Text(
          text = stringResource(
            R.string.result_regular_per_unit,
            pricePerUnit,
            displayUnit.displayName
          ),
          color = Color.White.copy(alpha = 0.7f),
          fontSize = Dimens.FontSizeCaption
        )
      }
    }
  }
}
