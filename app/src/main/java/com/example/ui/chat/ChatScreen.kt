package com.example.ui.chat

import android.app.Activity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Key
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.repository.ChatRepository
import com.example.ui.performance.PerformanceDialog
import com.example.ui.persona.PersonaDialog
import com.example.ui.settings.ApiKeyDialog
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.EmeraldGlow
import com.example.ui.theme.IndigoCyber
import com.example.ui.theme.VioletNeon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatScreen(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val currentSessionId by viewModel.currentSessionId.collectAsState()
    val currentPersona by viewModel.currentPersona.collectAsState()
    val filteredSessions by viewModel.filteredSessions.collectAsState()
    val messages by viewModel.messages.collectAsState()
    val isGenerating by viewModel.isGenerating.collectAsState()
    val streamingText by viewModel.streamingText.collectAsState()
    val attachedImage by viewModel.attachedImage.collectAsState()
    val performanceConfig by viewModel.performanceConfig.collectAsState()
    val memoryStats by viewModel.memoryStats.collectAsState()

    val isSpeaking by viewModel.tts.isSpeaking.collectAsState()
    val speakingMessageId by viewModel.tts.currentlySpeakingMessageId.collectAsState()

    val showApiKeyDialog by viewModel.showApiKeyDialog.collectAsState()
    val showPersonaDialog by viewModel.showPersonaDialog.collectAsState()
    val showPerformanceDialog by viewModel.showPerformanceDialog.collectAsState()
    val showClearHistoryConfirm by viewModel.showClearHistoryConfirm.collectAsState()
    val searchQuery by viewModel.sessionSearchQuery.collectAsState()

    var inputText by remember { mutableStateOf("") }
    val listState = rememberLazyListState()

    // Handle back button for closing drawer
    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    // Auto-scroll on new message or streaming update
    LaunchedEffect(messages.size, streamingText.length) {
        if (performanceConfig.autoScrollToBottom && (messages.isNotEmpty() || streamingText.isNotEmpty())) {
            val totalCount = messages.size + (if (isGenerating && streamingText.isNotEmpty()) 1 else 0)
            if (totalCount > 0) {
                listState.animateScrollToItem(totalCount - 1)
            }
        }
    }

    // Photo picker launcher (Zero-permission Android Photo Picker)
    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    val bitmap = BitmapFactory.decodeStream(stream)
                    if (bitmap != null) {
                        viewModel.attachImage(bitmap)
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Gagal memuat gambar: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    // Speech-to-Text Voice recognition launcher
    val speechRecognizerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spokenText = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spokenText.isNullOrBlank()) {
                inputText = if (inputText.isBlank()) spokenText else "$inputText $spokenText"
                viewModel.haptic.tick(performanceConfig.hapticEnabled)
            }
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                ChatDrawerContent(
                    sessions = filteredSessions,
                    currentSessionId = currentSessionId,
                    searchQuery = searchQuery,
                    onSearchQueryChange = { viewModel.sessionSearchQuery.value = it },
                    onSelectSession = { id ->
                        viewModel.selectSession(id)
                        scope.launch { drawerState.close() }
                    },
                    onNewChatClick = {
                        viewModel.createNewSession()
                        scope.launch { drawerState.close() }
                    },
                    onDeleteSession = { id ->
                        viewModel.deleteSession(id)
                    },
                    onClearAllHistory = {
                        viewModel.showClearHistoryConfirm.value = true
                    },
                    performanceConfig = performanceConfig,
                    memoryStats = memoryStats,
                    onOpenPerformanceDialog = {
                        viewModel.showPerformanceDialog.value = true
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            modifier = modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
            topBar = {
                CenterAlignedTopAppBar(
                    title = {
                        // Interactive Persona & Model Pill
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(20.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f))
                                .border(
                                    1.dp,
                                    currentPersona.accentColor.copy(alpha = 0.5f),
                                    RoundedCornerShape(20.dp)
                                )
                                .clickable { viewModel.showPersonaDialog.value = true }
                                .padding(horizontal = 10.dp, vertical = 4.dp)
                                .testTag("top_bar_persona_chip")
                        ) {
                            Text(text = currentPersona.emoji, fontSize = 16.sp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = currentPersona.name,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = { scope.launch { drawerState.open() } },
                            modifier = Modifier.testTag("open_drawer_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menu Riwayat",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    actions = {
                        // Tweak Performa Button
                        IconButton(
                            onClick = { viewModel.showPerformanceDialog.value = true },
                            modifier = Modifier.testTag("open_performance_dialog_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Speed,
                                contentDescription = "Tweak Performa",
                                tint = if (performanceConfig.isPotatoMode) CyanNeon else EmeraldGlow
                            )
                        }

                        // API Key Settings Button
                        val hasKey = viewModel.hasValidApiKey()
                        IconButton(
                            onClick = { viewModel.showApiKeyDialog.value = true },
                            modifier = Modifier.testTag("open_api_key_dialog_button")
                        ) {
                            Box {
                                Icon(
                                    imageVector = Icons.Default.Key,
                                    contentDescription = "Kunci API",
                                    tint = if (hasKey) CyanNeon else Color(0xFFF59E0B)
                                )
                                if (!hasKey) {
                                    // Yellow attention dot
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .align(Alignment.TopEnd)
                                            .clip(CircleShape)
                                            .background(Color(0xFFF59E0B))
                                    )
                                }
                            }
                        }

                        // New Chat Quick Action
                        IconButton(
                            onClick = { viewModel.createNewSession() },
                            modifier = Modifier.testTag("quick_new_chat_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Obrolan Baru",
                                tint = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    },
                    colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    )
                )
            },
            bottomBar = {
                ChatInputBar(
                    promptText = inputText,
                    onPromptChange = { inputText = it },
                    onSendClick = {
                        val text = inputText
                        inputText = ""
                        viewModel.sendMessage(text)
                    },
                    onStopClick = { viewModel.stopGenerating() },
                    isGenerating = isGenerating,
                    attachedBitmap = attachedImage,
                    onPickImageClick = {
                        photoPickerLauncher.launch(
                            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                        )
                    },
                    onVoiceClick = {
                        try {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Bicara pada Aura AI...")
                            }
                            speechRecognizerLauncher.launch(intent)
                        } catch (_: Exception) {
                            Toast.makeText(context, "Input suara tidak didukung di perangkat ini", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onRemoveImageClick = { viewModel.removeAttachedImage() }
                )
            }
        ) { paddingValues ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(paddingValues)
                    .background(MaterialTheme.colorScheme.background)
            ) {
                if (messages.isEmpty() && !isGenerating) {
                    EmptyChatSuggestions(
                        persona = currentPersona,
                        onSuggestionClick = { prompt ->
                            inputText = prompt
                            viewModel.sendMessage(prompt)
                            inputText = ""
                        },
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(
                            items = messages,
                            key = { it.id }
                        ) { message ->
                            MessageBubble(
                                message = message,
                                persona = currentPersona,
                                isSpeaking = isSpeaking && speakingMessageId == message.id,
                                onSpeakClick = {
                                    viewModel.tts.speak(message.id, message.content)
                                    viewModel.haptic.tick(performanceConfig.hapticEnabled)
                                },
                                onBookmarkClick = { viewModel.toggleBookmark(message) },
                                onDeleteClick = { viewModel.deleteMessage(message.id) }
                            )
                        }

                        // Live Streaming Bubble
                        if (isGenerating) {
                            item {
                                StreamingBubble(
                                    streamingText = streamingText,
                                    persona = currentPersona
                                )
                            }
                        }

                        // Bottom Spacer
                        item {
                            Spacer(modifier = Modifier.height(16.dp))
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    if (showApiKeyDialog) {
        ApiKeyDialog(
            initialKey = viewModel.getEffectiveApiKey(),
            onSaveKey = { newKey ->
                viewModel.updatePerformanceConfig(performanceConfig.copy(customApiKey = newKey))
                Toast.makeText(context, "Kunci API berhasil disimpan!", Toast.LENGTH_SHORT).show()
            },
            onDismiss = { viewModel.showApiKeyDialog.value = false },
            chatRepository = remember { ChatRepository(context) }
        )
    }

    if (showPerformanceDialog) {
        PerformanceDialog(
            config = performanceConfig,
            memoryStats = memoryStats,
            onConfigChange = { newConfig ->
                viewModel.updatePerformanceConfig(newConfig)
            },
            onCleanCacheClick = {
                viewModel.cleanCache()
            },
            onDismiss = { viewModel.showPerformanceDialog.value = false }
        )
    }

    if (showPersonaDialog) {
        PersonaDialog(
            currentPersona = currentPersona,
            onSelectPersona = { newPersona ->
                viewModel.changePersona(newPersona)
            },
            onDismiss = { viewModel.showPersonaDialog.value = false }
        )
    }

    if (showClearHistoryConfirm) {
        AlertDialog(
            onDismissRequest = { viewModel.showClearHistoryConfirm.value = false },
            title = { Text("Hapus Semua Riwayat?", fontWeight = FontWeight.Bold) },
            text = { Text("Semua percakapan dan pesan akan dihapus secara permanen dari perangkat.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearAllHistory()
                        viewModel.showClearHistoryConfirm.value = false
                        Toast.makeText(context, "Semua riwayat dibersihkan", Toast.LENGTH_SHORT).show()
                    }
                ) {
                    Text("Hapus Semua", color = Color(0xFFEF4444), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.showClearHistoryConfirm.value = false }) {
                    Text("Batal")
                }
            }
        )
    }
}
