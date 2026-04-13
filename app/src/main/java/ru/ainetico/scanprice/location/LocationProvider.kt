package ru.ainetico.scanprice.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.util.Log
import androidx.core.content.ContextCompat
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import kotlinx.coroutines.tasks.await

class LocationProvider(private val context: Context) {

  private val fusedClient = LocationServices.getFusedLocationProviderClient(context)

  @SuppressLint("MissingPermission")
  suspend fun getCurrentLocation(): Location? {
    if (!hasPermission()) {
      Log.d("LocationProvider", "Location permission not granted, skipping")
      return null
    }

    return try {
      val cancellationToken = CancellationTokenSource()
      fusedClient.getCurrentLocation(
        Priority.PRIORITY_HIGH_ACCURACY,
        cancellationToken.token
      ).await()
    } catch (e: Exception) {
      Log.w("LocationProvider", "Failed to get location: ${e.message}")
      null
    }
  }

  private fun hasPermission(): Boolean {
    return ContextCompat.checkSelfPermission(
      context,
      Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(
              context,
              Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED
  }
}
