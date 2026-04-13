package ru.ainetico.honestprice.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.TileService
import ru.ainetico.honestprice.MainActivity

class ScanTileService : TileService() {

  override fun onClick() {
    val intent = Intent(this, MainActivity::class.java).apply {
      action = "ru.ainetico.honestprice.ACTION_SCAN"
      addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    if (Build.VERSION.SDK_INT >= 34) {
      val pendingIntent = PendingIntent.getActivity(
        this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
      )
      startActivityAndCollapse(pendingIntent)
    } else {
      @Suppress("DEPRECATION")
      startActivityAndCollapse(intent)
    }
  }
}
