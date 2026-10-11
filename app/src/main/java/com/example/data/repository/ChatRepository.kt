package com.example.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.example.BuildConfig
import com.example.data.local.AppDatabase
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.model.AIPersona
import com.example.data.model.PerformanceConfig
import com.example.data.model.PersonaPresets
import com.example.data.remote.GeminiMessagePayload
import com.example.data.remote.GeminiRemoteDataSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class ChatRepository(
    private val context: Context,
    private val database: AppDatabase = AppDatabase.getDatabase(context),
    private val remoteSource: GeminiRemoteDataSource = GeminiRemoteDataSource()
) {
    private val chatDao = database.chatDao()
    private val prefs: SharedPreferences =
        context.getSharedPreferences("aura_ai_prefs", Context.MODE_PRIVATE)

    private val _performanceConfig = MutableStateFlow(loadPerformanceConfig())
    val performanceConfig: StateFlow<PerformanceConfig> = _performanceConfig.asStateFlow()

    private var activeGenerationJob: Job? = null

    // Sessions flow
    fun getAllSessions(): Flow<List<ChatSessionEntity>> = chatDao.getAllSessions()

    // Messages flow for session
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        chatDao.getMessagesForSession(sessionId)

    suspend fun getSessionById(sessionId: Long): ChatSessionEntity? =
        chatDao.getSessionById(sessionId)

    suspend fun createNewSession(
        title: String = "Obrolan Baru",
        personaId: String = "aura",
        modelName: String = _performanceConfig.value.selectedModel
    ): Long {
        val persona = PersonaPresets.getById(personaId)
        val session = ChatSessionEntity(
            title = title,
            personaId = personaId,
            modelName = modelName,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )
        val sessionId = chatDao.insertSession(session)

        // Insert initial greeting message
        val greeting = ChatMessageEntity(
            sessionId = sessionId,
            role = "assistant",
            content = persona.initialGreeting,
            timestamp = System.currentTimeMillis()
        )
        chatDao.insertMessage(greeting)

        return sessionId
    }

    suspend fun updateSessionPersona(sessionId: Long, personaId: String) {
        val session = chatDao.getSessionById(sessionId) ?: return
        chatDao.updateSession(session.copy(personaId = personaId, updatedAt = System.currentTimeMillis()))
    }

    suspend fun updateSessionTitle(sessionId: Long, newTitle: String) {
        val session = chatDao.getSessionById(sessionId) ?: return
        chatDao.updateSession(session.copy(title = newTitle, updatedAt = System.currentTimeMillis()))
    }

    suspend fun deleteSession(sessionId: Long) {
        chatDao.deleteSession(sessionId)
    }

    suspend fun deleteAllSessions() {
        chatDao.deleteAllSessions()
    }

    suspend fun toggleBookmark(message: ChatMessageEntity) {
        chatDao.updateMessage(message.copy(isBookmarked = !message.isBookmarked))
    }

    suspend fun deleteMessage(messageId: Long) {
        chatDao.deleteMessage(messageId)
    }

    fun getEffectiveApiKey(): String {
        val customKey = _performanceConfig.value.customApiKey.trim()
        if (customKey.isNotEmpty()) {
            return customKey
        }
        val buildConfigKey = try {
            BuildConfig.GEMINI_API_KEY
        } catch (_: Exception) {
            ""
        }
        return if (buildConfigKey != "MY_GEMINI_API_KEY" && buildConfigKey.isNotBlank()) {
            buildConfigKey
        } else {
            ""
        }
    }

    fun hasValidApiKey(): Boolean {
        return getEffectiveApiKey().isNotBlank()
    }

    /**
     * Sends user message, generates AI response with streaming, and commits to Room DB.
     */
    fun sendMessageAndStreamResponse(
        scope: CoroutineScope,
        sessionId: Long,
        userPrompt: String,
        imageBase64: String?,
        persona: AIPersona,
        onChunk: (currentFullText: String) -> Unit,
        onComplete: (isSuccess: Boolean, errorMsg: String?) -> Unit
    ) {
        activeGenerationJob?.cancel()
        activeGenerationJob = scope.launch(Dispatchers.IO) {
            val startTime = System.currentTimeMillis()

            // 1. Insert user message
            val userMsg = ChatMessageEntity(
                sessionId = sessionId,
                role = "user",
                content = userPrompt,
                imageBase64 = imageBase64,
                timestamp = System.currentTimeMillis()
            )
            chatDao.insertMessage(userMsg)

            // Update session title if default
            val currentSession = chatDao.getSessionById(sessionId)
            if (currentSession != null && currentSession.title.startsWith("Obrolan Baru")) {
                val shortTitle = userPrompt.take(28).trim().let {
                    if (it.length >= 28) "$it..." else it
                }
                chatDao.updateSession(
                    currentSession.copy(
                        title = shortTitle,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            } else if (currentSession != null) {
                chatDao.updateSession(currentSession.copy(updatedAt = System.currentTimeMillis()))
            }

            // 2. Insert placeholder assistant message
            val assistantMsgId = chatDao.insertMessage(
                ChatMessageEntity(
                    sessionId = sessionId,
                    role = "assistant",
                    content = "",
                    timestamp = System.currentTimeMillis()
                )
            )

            val effectiveApiKey = getEffectiveApiKey()
            val modelName = _performanceConfig.value.selectedModel

            // If API key is not yet set, give polite helpful guidance instead of hard crash
            if (effectiveApiKey.isBlank()) {
                val demoResponse = """
                    ⚠️ **Kunci API Belum Dikonfigurasi**
                    
                    Aura AI siap digunakan secara maksimal! Untuk mulai mengobrol dengan kecerdasan Gemini:
                    1. Buka icon **Kunci API** di pojok kanan atas atau Pengaturan.
                    2. Masukkan Google AI Studio API Key Anda (dapat dibuat gratis di aistudio.google.com).
                    3. Tekan **Simpan**.
                    
                    *Catatan: Aplikasi ini sudah teroptimasi dengan Mode HP Kentang & 60/120 FPS Physics untuk pengalaman paling mulus tanpa lag!*
                """.trimIndent()

                chatDao.updateMessage(
                    ChatMessageEntity(
                        id = assistantMsgId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = demoResponse,
                        timestamp = System.currentTimeMillis(),
                        latencyMs = System.currentTimeMillis() - startTime
                    )
                )
                onChunk(demoResponse)
                onComplete(true, null)
                return@launch
            }

            // Prepare conversation history (last 10 messages for low latency & memory)
            val pastMessages = chatDao.getMessagesListForSession(sessionId)
                .filter { it.id != assistantMsgId && it.content.isNotBlank() }
                .takeLast(10)

            val payloadHistory = pastMessages.map {
                GeminiMessagePayload(
                    role = if (it.role == "assistant") "model" else "user",
                    text = it.content,
                    imageBase64 = it.imageBase64
                )
            }

            val accumulatedText = StringBuilder()
            var lastUiUpdate = System.currentTimeMillis()

            try {
                remoteSource.streamGenerateContent(
                    apiKey = effectiveApiKey,
                    modelName = modelName,
                    persona = persona,
                    history = payloadHistory,
                    temperature = persona.temperature
                ).collect { chunk ->
                    accumulatedText.append(chunk)
                    val now = System.currentTimeMillis()

                    // Debounce UI update to 35ms on potato phones for silky smooth 60fps
                    val debounceTime = if (_performanceConfig.value.isPotatoMode) 60L else 30L
                    if (now - lastUiUpdate > debounceTime) {
                        lastUiUpdate = now
                        onChunk(accumulatedText.toString())
                    }
                }

                val finalFullText = accumulatedText.toString().trim()
                val latency = System.currentTimeMillis() - startTime
                val wordCount = finalFullText.split("\\s+".toRegex()).size

                // Final DB update
                chatDao.updateMessage(
                    ChatMessageEntity(
                        id = assistantMsgId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = finalFullText.ifEmpty { "Maaf, respon tidak diterima." },
                        timestamp = System.currentTimeMillis(),
                        tokenCount = wordCount,
                        latencyMs = latency
                    )
                )
                onChunk(finalFullText)
                onComplete(true, null)
            } catch (e: Exception) {
                val errText = "❌ Gagal memproses: ${e.localizedMessage ?: "Terjadi kesalahan jaringan"}"
                chatDao.updateMessage(
                    ChatMessageEntity(
                        id = assistantMsgId,
                        sessionId = sessionId,
                        role = "assistant",
                        content = errText,
                        timestamp = System.currentTimeMillis()
                    )
                )
                onChunk(errText)
                onComplete(false, e.localizedMessage)
            }
        }
    }

    fun stopGenerating() {
        activeGenerationJob?.cancel()
        activeGenerationJob = null
    }

    suspend fun testConnection(apiKey: String): Boolean {
        return remoteSource.testApiKeyConnection(apiKey)
    }

    fun updatePerformanceConfig(newConfig: PerformanceConfig) {
        _performanceConfig.value = newConfig
        savePerformanceConfig(newConfig)
    }

    private fun loadPerformanceConfig(): PerformanceConfig {
        return PerformanceConfig(
            isPotatoMode = prefs.getBoolean("isPotatoMode", false),
            smooth60FpsTweak = prefs.getBoolean("smooth60FpsTweak", true),
            hapticEnabled = prefs.getBoolean("hapticEnabled", true),
            streamTypewriterSpeedMs = prefs.getLong("streamTypewriterSpeedMs", 15L),
            autoScrollToBottom = prefs.getBoolean("autoScrollToBottom", true),
            selectedModel = prefs.getString("selectedModel", "gemini-3.5-flash") ?: "gemini-3.5-flash",
            customApiKey = prefs.getString("customApiKey", "") ?: "",
            customTemperature = prefs.getFloat("customTemperature", 0.7f)
        )
    }

    private fun savePerformanceConfig(config: PerformanceConfig) {
        prefs.edit()
            .putBoolean("isPotatoMode", config.isPotatoMode)
            .putBoolean("smooth60FpsTweak", config.smooth60FpsTweak)
            .putBoolean("hapticEnabled", config.hapticEnabled)
            .putLong("streamTypewriterSpeedMs", config.streamTypewriterSpeedMs)
            .putBoolean("autoScrollToBottom", config.autoScrollToBottom)
            .putString("selectedModel", config.selectedModel)
            .putString("customApiKey", config.customApiKey)
            .putFloat("customTemperature", config.customTemperature)
            .apply()
    }
}
