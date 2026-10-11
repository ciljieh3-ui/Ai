package com.example

import com.example.data.model.PerformanceConfig
import com.example.data.model.PersonaPresets
import com.example.util.PerformanceMonitor
import org.junit.Assert.*
import org.junit.Test

class ExampleUnitTest {
    @Test
    fun testPersonaPresets_containsDefaultAura() {
        val defaultPersona = PersonaPresets.defaultPersona
        assertEquals("aura", defaultPersona.id)
        assertTrue(PersonaPresets.allPresets.isNotEmpty())
        assertEquals(defaultPersona, PersonaPresets.getById("aura"))
    }

    @Test
    fun testPerformanceMonitor_memoryStatsAreValid() {
        val stats = PerformanceMonitor.getMemoryStats()
        assertTrue(stats.maxMemoryMb > 0)
        assertTrue(stats.usedPercentage in 0..100)
    }

    @Test
    fun testPerformanceConfig_defaults() {
        val config = PerformanceConfig()
        assertTrue(config.smooth60FpsTweak)
        assertTrue(config.hapticEnabled)
        assertEquals("gemini-3.5-flash", config.selectedModel)
    }
}
