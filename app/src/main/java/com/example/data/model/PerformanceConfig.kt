package com.example.data.model

data class PerformanceConfig(
    val isPotatoMode: Boolean = false, // Mode HP Kentang (Hemat RAM & GPU)
    val smooth60FpsTweak: Boolean = true, // Optimasi render 60-120 FPS
    val hapticEnabled: Boolean = true, // Haptic feedback getar halus
    val streamTypewriterSpeedMs: Long = 15L, // Kecepatan efek ketik (15ms default)
    val autoScrollToBottom: Boolean = true,
    val selectedModel: String = "gemini-3.5-flash",
    val customApiKey: String = "",
    val customTemperature: Float = 0.7f
) {
    companion object {
        val SUPPORTED_MODELS = listOf(
            ModelOption(
                id = "gemini-3.5-flash",
                name = "Gemini 3.5 Flash",
                badge = "Rekomendasi",
                description = "Seimbang, pintar, cepat, dan hemat kuota/token"
            ),
            ModelOption(
                id = "gemini-3.1-flash-lite-preview",
                name = "Gemini 3.1 Flash-Lite",
                badge = "Ultra Cepat",
                description = "Latency paling rendah, cocok untuk respon instan dan HP spek hemat"
            ),
            ModelOption(
                id = "gemini-3.1-pro-preview",
                name = "Gemini 3.1 Pro",
                badge = "Nalar Tinggi",
                description = "Penalaran matematika, koding rumit, dan analisis mendalam"
            )
        )
    }
}

data class ModelOption(
    val id: String,
    val name: String,
    val badge: String,
    val description: String
)
