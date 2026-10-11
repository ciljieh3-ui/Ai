package com.example.ui.chat

import android.app.Application
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.model.AIPersona
import com.example.data.model.PerformanceConfig
import com.example.data.model.PersonaPresets
import com.example.data.remote.toBase64Jpeg
import com.example.data.repository.ChatRepository
import com.example.util.HapticFeedbackHelper
import com.example.util.MemoryStats
import com.example.util.PerformanceMonitor
import com.example.util.TtsHelper
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = ChatRepository(application)
    val haptic = HapticFeedbackHelper(application)
    val tts = TtsHelper(application)

    val performanceConfig: StateFlow<PerformanceConfig> = repository.performanceConfig

    private val _currentSessionId = MutableStateFlow<Long?>(null)
    val currentSessionId: StateFlow<Long?> = _currentSessionId.asStateFlow()

    private val _currentPersona = MutableStateFlow(PersonaPresets.defaultPersona)
    val currentPersona: StateFlow<AIPersona> = _currentPersona.asStateFlow()

    val sessions: StateFlow<List<ChatSessionEntity>> = repository.getAllSessions()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _messages = MutableStateFlow<List<ChatMessageEntity>>(emptyList())
    val messages: StateFlow<List<ChatMessageEntity>> = _messages.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _streamingText = MutableStateFlow("")
    val streamingText: StateFlow<String> = _streamingText.asStateFlow()

    private val _attachedImage = MutableStateFlow<Bitmap?>(null)
    val attachedImage: StateFlow<Bitmap?> = _attachedImage.asStateFlow()

    private val _memoryStats = MutableStateFlow(PerformanceMonitor.getMemoryStats())
    val memoryStats: StateFlow<MemoryStats> = _memoryStats.asStateFlow()

    // Dialog & UI Visibility states
    val showApiKeyDialog = MutableStateFlow(false)
    val showPersonaDialog = MutableStateFlow(false)
    val showPerformanceDialog = MutableStateFlow(false)
    val showClearHistoryConfirm = MutableStateFlow(false)
    val sessionSearchQuery = MutableStateFlow("")

    val filteredSessions = combine(sessions, sessionSearchQuery) { list, query ->
        if (query.isBlank()) list
        else list.filter { it.title.contains(query, ignoreCase = true) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            // Observe sessions and pick the first or create a new one
            sessions.collect { list ->
                if (_currentSessionId.value == null && list.isNotEmpty()) {
                    selectSession(list.first().id)
                } else if (_currentSessionId.value == null && list.isEmpty()) {
                    createNewSession()
                }
            }
        }
    }

    fun selectSession(sessionId: Long) {
        _currentSessionId.value = sessionId
        viewModelScope.launch {
            val session = repository.getSessionById(sessionId)
            if (session != null) {
                _currentPersona.value = PersonaPresets.getById(session.personaId)
            }
            repository.getMessagesForSession(sessionId).collect { msgs ->
                _messages.value = msgs
            }
        }
    }

    fun createNewSession(personaId: String = _currentPersona.value.id) {
        viewModelScope.launch {
            val newId = repository.createNewSession(personaId = personaId)
            selectSession(newId)
            haptic.tick(performanceConfig.value.hapticEnabled)
        }
    }

    fun changePersona(persona: AIPersona) {
        _currentPersona.value = persona
        val sessionId = _currentSessionId.value
        if (sessionId != null) {
            viewModelScope.launch {
                repository.updateSessionPersona(sessionId, persona.id)
            }
        }
        haptic.tick(performanceConfig.value.hapticEnabled)
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            repository.deleteSession(sessionId)
            if (_currentSessionId.value == sessionId) {
                val remaining = sessions.value.filter { it.id != sessionId }
                if (remaining.isNotEmpty()) {
                    selectSession(remaining.first().id)
                } else {
                    _currentSessionId.value = null
                    createNewSession()
                }
            }
            haptic.tick(performanceConfig.value.hapticEnabled)
        }
    }

    fun clearAllHistory() {
        viewModelScope.launch {
            repository.deleteAllSessions()
            _currentSessionId.value = null
            createNewSession()
            haptic.success(performanceConfig.value.hapticEnabled)
        }
    }

    fun attachImage(bitmap: Bitmap) {
        _attachedImage.value = bitmap
        haptic.tick(performanceConfig.value.hapticEnabled)
    }

    fun removeAttachedImage() {
        _attachedImage.value = null
    }

    fun sendMessage(prompt: String) {
        val trimmed = prompt.trim()
        val bitmap = _attachedImage.value
        if (trimmed.isEmpty() && bitmap == null) return

        val sessionId = _currentSessionId.value ?: return
        val imageBase64 = bitmap?.toBase64Jpeg()
        _attachedImage.value = null

        _isGenerating.value = true
        _streamingText.value = ""
        haptic.tick(performanceConfig.value.hapticEnabled)

        repository.sendMessageAndStreamResponse(
            scope = viewModelScope,
            sessionId = sessionId,
            userPrompt = trimmed,
            imageBase64 = imageBase64,
            persona = _currentPersona.value,
            onChunk = { text ->
                _streamingText.value = text
            },
            onComplete = { _, _ ->
                _isGenerating.value = false
                _streamingText.value = ""
                refreshMemoryStats()
                haptic.success(performanceConfig.value.hapticEnabled)
            }
        )
    }

    fun stopGenerating() {
        repository.stopGenerating()
        _isGenerating.value = false
        _streamingText.value = ""
        haptic.tick(performanceConfig.value.hapticEnabled)
    }

    fun toggleBookmark(message: ChatMessageEntity) {
        viewModelScope.launch {
            repository.toggleBookmark(message)
            haptic.tick(performanceConfig.value.hapticEnabled)
        }
    }

    fun deleteMessage(messageId: Long) {
        viewModelScope.launch {
            repository.deleteMessage(messageId)
            haptic.tick(performanceConfig.value.hapticEnabled)
        }
    }

    fun updatePerformanceConfig(newConfig: PerformanceConfig) {
        repository.updatePerformanceConfig(newConfig)
        haptic.tick(newConfig.hapticEnabled)
    }

    fun hasValidApiKey(): Boolean = repository.hasValidApiKey()

    fun getEffectiveApiKey(): String = repository.getEffectiveApiKey()

    fun refreshMemoryStats() {
        _memoryStats.value = PerformanceMonitor.getMemoryStats()
    }

    fun cleanCache() {
        PerformanceMonitor.clearCache(getApplication())
        refreshMemoryStats()
        haptic.success(performanceConfig.value.hapticEnabled)
    }

    override fun onCleared() {
        super.onCleared()
        tts.shutdown()
    }
}
