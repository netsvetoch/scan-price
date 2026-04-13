package ru.ainetico.scanprice.navigation

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import ru.ainetico.scanprice.data.Scan
import ru.ainetico.scanprice.data.ScanStatus
import ru.ainetico.scanprice.model.AnalysisResult
import ru.ainetico.scanprice.model.ParsedPriceTag
import java.math.BigDecimal

class AppNavigationViewModelTest {

    @Test
    fun `initial state has no pending result or scan`() {
        val vm = AppNavigationViewModel()
        val state = vm.state.value

        assertNull(state.pendingResult)
        assertNull(state.pendingScan)
        assertFalse(state.showCameraSheet)
    }

    @Test
    fun `initial state respects initialShowCamera parameter`() {
        val vm = AppNavigationViewModel(initialShowCamera = true)
        assertTrue(vm.state.value.showCameraSheet)
    }

    @Test
    fun `setPendingResult stores scanId and result`() {
        val vm = AppNavigationViewModel()
        val tag = ParsedPriceTag(productName = "Молоко", priceRegular = BigDecimal("89.90"))
        val result = AnalysisResult(tag = tag, price = null)

        vm.setPendingResult(42L, result)

        val pending = vm.state.value.pendingResult
        assertNotNull(pending)
        assertEquals(42L, pending!!.first)
        assertEquals("Молоко", pending.second.tag.productName)
    }

    @Test
    fun `clearPendingResult removes pending result`() {
        val vm = AppNavigationViewModel()
        val result = AnalysisResult(tag = ParsedPriceTag(), price = null)
        vm.setPendingResult(1L, result)

        vm.clearPendingResult()

        assertNull(vm.state.value.pendingResult)
    }

    @Test
    fun `setPendingScan stores scan`() {
        val vm = AppNavigationViewModel()
        val scan = Scan(id = 5, productName = "Хлеб", status = ScanStatus.COMPLETED)

        vm.setPendingScan(scan)

        val pending = vm.state.value.pendingScan
        assertNotNull(pending)
        assertEquals(5L, pending!!.id)
        assertEquals("Хлеб", pending.productName)
    }

    @Test
    fun `setPendingScan with null clears scan`() {
        val vm = AppNavigationViewModel()
        val scan = Scan(id = 5, productName = "Хлеб")
        vm.setPendingScan(scan)

        vm.setPendingScan(null)

        assertNull(vm.state.value.pendingScan)
    }

    @Test
    fun `setShowCameraSheet toggles camera sheet visibility`() {
        val vm = AppNavigationViewModel()

        vm.setShowCameraSheet(true)
        assertTrue(vm.state.value.showCameraSheet)

        vm.setShowCameraSheet(false)
        assertFalse(vm.state.value.showCameraSheet)
    }

    @Test
    fun `state updates are independent — setting result does not affect scan`() {
        val vm = AppNavigationViewModel()
        val scan = Scan(id = 1, productName = "Сыр")
        vm.setPendingScan(scan)

        val result = AnalysisResult(tag = ParsedPriceTag(productName = "Масло"), price = null)
        vm.setPendingResult(2L, result)

        // Both should coexist
        assertNotNull(vm.state.value.pendingScan)
        assertNotNull(vm.state.value.pendingResult)
        assertEquals("Сыр", vm.state.value.pendingScan!!.productName)
        assertEquals("Масло", vm.state.value.pendingResult!!.second.tag.productName)
    }

    @Test
    fun `clearPendingResult does not affect other state fields`() {
        val vm = AppNavigationViewModel()
        val scan = Scan(id = 1, productName = "Кефир")
        vm.setPendingScan(scan)
        vm.setShowCameraSheet(true)
        vm.setPendingResult(1L, AnalysisResult(tag = ParsedPriceTag(), price = null))

        vm.clearPendingResult()

        assertNull(vm.state.value.pendingResult)
        assertNotNull(vm.state.value.pendingScan)
        assertTrue(vm.state.value.showCameraSheet)
    }
}
