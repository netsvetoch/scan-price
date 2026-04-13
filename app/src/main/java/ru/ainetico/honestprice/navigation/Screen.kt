package ru.ainetico.honestprice.navigation

sealed class Screen(val route: String) {
  object Onboarding : Screen("onboarding")
  object Camera : Screen("camera?openGallery={openGallery}") {
    fun createRoute(openGallery: Boolean = false) = "camera?openGallery=$openGallery"
  }

  object Result : Screen("result/{scanId}") {
    fun createRoute(scanId: Long) = "result/$scanId"
  }

  object ResultManual : Screen("result_manual")
  object History : Screen("history")
  object Settings : Screen("settings")
}
