package ru.ainetico.scanprice.model

import org.junit.Assert.*
import org.junit.Test

class WeightUnitTest {

    @Test
    fun `G base unit is KG`() {
        assertEquals(WeightUnit.KG, WeightUnit.G.baseUnit)
    }

    @Test
    fun `KG base unit is null`() {
        assertNull(WeightUnit.KG.baseUnit)
    }

    @Test
    fun `ML base unit is L`() {
        assertEquals(WeightUnit.L, WeightUnit.ML.baseUnit)
    }

    @Test
    fun `L base unit is null`() {
        assertNull(WeightUnit.L.baseUnit)
    }

    @Test
    fun `PCS base unit is null`() {
        assertNull(WeightUnit.PCS.baseUnit)
    }

    @Test
    fun `all base units are themselves base`() {
        for (unit in WeightUnit.entries) {
            if (unit.baseUnit != null) {
                assertNull(unit.baseUnit!!.baseUnit)
            }
        }
    }
}
