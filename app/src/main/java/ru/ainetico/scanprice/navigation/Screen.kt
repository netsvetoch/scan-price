package ru.ainetico.scanprice.navigation

sealed class Screen(val route: String) {
  object Onboarding : Screen("onboarding")

  object Result : Screen("result/{scanId}") {
    fun createRoute(scanId: Long) = "result/$scanId"
  }

  object ResultManual : Screen("result_manual")
  object History : Screen("history")
}
