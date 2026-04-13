package ru.ainetico.honestprice.ui.camera

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.ui.theme.Dimens

/**
 * Shown when camera permission is not granted.
 * Displays explanation text, a grant/settings button, and bottom gallery/manual controls.
 */
@Composable
fun CameraPermissionContent(
  permissionDeniedPermanently: Boolean,
  onRequestPermission: () -> Unit,
  onOpenSettings: () -> Unit,
  onGallery: () -> Unit,
  onManualEntry: () -> Unit,
  modifier: Modifier = Modifier
) {
  LocalContext.current

  Column(
    modifier = modifier
        .fillMaxSize()
        .padding(32.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Text(
      text = stringResource(R.string.camera_permission_needed),
      color = MaterialTheme.colorScheme.onSurface,
      fontSize = Dimens.FontSizeBody,
      textAlign = TextAlign.Center
    )
    Spacer(modifier = Modifier.height(16.dp))
    Button(
      onClick = {
        if (permissionDeniedPermanently) {
          onOpenSettings()
        } else {
          onRequestPermission()
        }
      }
    ) {
      Text(stringResource(R.string.onboarding_allow))
    }
  }
}

/**
 * Bottom controls row for gallery and manual entry — shared between permission and camera screens.
 */
@Composable
fun BottomGalleryControls(
  onGallery: () -> Unit,
  onManualEntry: () -> Unit,
  captureButton: @Composable () -> Unit,
  modifier: Modifier = Modifier
) {
  Row(
    modifier = modifier
        .fillMaxWidth()
        .padding(
            bottom = Dimens.BottomControlsBottomPadding,
            start = Dimens.BottomControlsSidePadding,
            end = Dimens.BottomControlsSidePadding
        ),
    horizontalArrangement = Arrangement.SpaceBetween,
    verticalAlignment = Alignment.CenterVertically
  ) {
    Button(
      onClick = onGallery,
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.size(Dimens.ActionButtonSize),
      contentPadding = PaddingValues(0.dp),
      shape = CircleShape
    ) {
      Icon(
        Icons.Filled.PhotoLibrary,
        contentDescription = stringResource(R.string.camera_gallery),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    captureButton()
    Button(
      onClick = onManualEntry,
      colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      modifier = Modifier.size(Dimens.ActionButtonSize),
      contentPadding = PaddingValues(0.dp),
      shape = CircleShape
    ) {
      Icon(
        Icons.Filled.Edit,
        contentDescription = stringResource(R.string.camera_manual),
        tint = MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
  }
}
