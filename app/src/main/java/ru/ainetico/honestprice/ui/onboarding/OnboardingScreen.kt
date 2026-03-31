package ru.ainetico.honestprice.ui.onboarding

import android.Manifest
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import ru.ainetico.honestprice.R
import ru.ainetico.honestprice.model.ModelDownloader

private fun completeOnboarding(context: Context, onComplete: () -> Unit) {
  context.getSharedPreferences("honest_price_prefs", Context.MODE_PRIVATE)
    .edit()
    .putBoolean("onboarding_completed", true)
    .apply()
  onComplete()
}

@Composable
fun OnboardingScreen(modelDownloader: ModelDownloader, onComplete: () -> Unit) {
  val context = LocalContext.current
  val pagerState = rememberPagerState(pageCount = { 3 })
  val coroutineScope = rememberCoroutineScope()
  val downloadState by modelDownloader.state.collectAsState()

  // Start download on first page
  LaunchedEffect(Unit) {
    modelDownloader.startDownloadIfNeeded()
  }

  val notificationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ ->
    coroutineScope.launch {
      pagerState.animateScrollToPage(1)
    }
  }

  val cameraPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ ->
    coroutineScope.launch {
      pagerState.animateScrollToPage(2)
    }
  }

  val locationPermissionLauncher = rememberLauncherForActivityResult(
    ActivityResultContracts.RequestPermission()
  ) { _ ->
    completeOnboarding(context, onComplete)
  }

  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(16.dp),
    horizontalAlignment = Alignment.CenterHorizontally
  ) {
    HorizontalPager(
      state = pagerState,
      modifier = Modifier.weight(1f)
    ) { page ->
      when (page) {
        0 -> ModelDownloadPage(downloadState)
        1 -> CameraPage()
        2 -> LocationPage()
      }
    }

    // Dot indicator
    Row(
      modifier = Modifier
        .fillMaxWidth()
        .padding(vertical = 16.dp),
      horizontalArrangement = Arrangement.Center
    ) {
      repeat(3) { index ->
        val color = if (pagerState.currentPage == index) {
          MaterialTheme.colorScheme.primary
        } else {
          MaterialTheme.colorScheme.onSurface.copy(alpha = 0.3f)
        }
        Box(
          modifier = Modifier
            .padding(horizontal = 4.dp)
            .size(8.dp)
            .background(color, CircleShape)
        )
      }
    }

    // Action buttons
    when (pagerState.currentPage) {
      0 -> {
        Button(
          onClick = {
            if (android.os.Build.VERSION.SDK_INT >= 33) {
              notificationPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } else {
              coroutineScope.launch { pagerState.animateScrollToPage(1) }
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.onboarding_allow_notifications))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = {
            coroutineScope.launch { pagerState.animateScrollToPage(1) }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.onboarding_skip))
        }
      }

      1 -> {
        Button(
          onClick = {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.onboarding_allow))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = {
            coroutineScope.launch {
              pagerState.animateScrollToPage(2)
            }
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.onboarding_skip))
        }
      }

      2 -> {
        Button(
          onClick = {
            locationPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
          },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.onboarding_allow))
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(
          onClick = { completeOnboarding(context, onComplete) },
          modifier = Modifier.fillMaxWidth()
        ) {
          Text(stringResource(R.string.onboarding_skip))
        }
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
  }
}

@Composable
private fun ModelDownloadPage(downloadState: ModelDownloader.DownloadState) {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = Icons.Filled.CloudDownload,
      contentDescription = null,
      modifier = Modifier.size(96.dp),
      tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = stringResource(R.string.onboarding_download_title),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.onboarding_download_description),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(16.dp))

    when (downloadState) {
      is ModelDownloader.DownloadState.Idle -> {
        Text(
          text = stringResource(R.string.onboarding_download_preparing),
          style = MaterialTheme.typography.bodyMedium,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.onSurfaceVariant
        )
      }

      is ModelDownloader.DownloadState.Downloading -> {
        FileProgressRow(downloadState.file1)
        Spacer(modifier = Modifier.height(8.dp))
        FileProgressRow(downloadState.file2)
      }

      is ModelDownloader.DownloadState.Completed -> {
        Text(
          text = stringResource(R.string.onboarding_download_done),
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.primary
        )
      }

      is ModelDownloader.DownloadState.Error -> {
        Text(
          text = downloadState.message,
          style = MaterialTheme.typography.bodyLarge,
          textAlign = TextAlign.Center,
          color = MaterialTheme.colorScheme.error
        )
      }
    }

    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.onboarding_download_hint),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )

  }
}

@Composable
private fun FileProgressRow(fp: ModelDownloader.FileProgress) {
  Column(modifier = Modifier.fillMaxWidth()) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween
    ) {
      Text(
        text = fp.label,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurface
      )
      Text(
        text = if (fp.done) "✓" else "${fp.progress}%",
        style = MaterialTheme.typography.bodySmall,
        color = if (fp.done) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
      )
    }
    Spacer(modifier = Modifier.height(4.dp))
    LinearProgressIndicator(
      progress = { fp.progress / 100f },
      modifier = Modifier.fillMaxWidth()
    )
  }
}

@Composable
private fun CameraPage() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = Icons.Filled.CameraAlt,
      contentDescription = null,
      modifier = Modifier.size(96.dp),
      tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = stringResource(R.string.onboarding_camera_title),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.onboarding_camera_text),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(12.dp))
    Text(
      text = stringResource(R.string.onboarding_camera_hint),
      style = MaterialTheme.typography.bodyMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}

@Composable
private fun LocationPage() {
  Column(
    modifier = Modifier
      .fillMaxSize()
      .padding(24.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.Center
  ) {
    Icon(
      imageVector = Icons.Filled.LocationOn,
      contentDescription = null,
      modifier = Modifier.size(96.dp),
      tint = MaterialTheme.colorScheme.primary
    )
    Spacer(modifier = Modifier.height(24.dp))
    Text(
      text = stringResource(R.string.onboarding_location_title),
      style = MaterialTheme.typography.headlineMedium,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )
    Spacer(modifier = Modifier.height(16.dp))
    Text(
      text = stringResource(R.string.onboarding_location_text),
      style = MaterialTheme.typography.bodyLarge,
      textAlign = TextAlign.Center,
      color = MaterialTheme.colorScheme.onSurface
    )
  }
}
