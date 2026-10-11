package com.example.ui.chat

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.local.ChatMessageEntity
import com.example.data.local.ChatSessionEntity
import com.example.data.model.AIPersona
import com.example.ui.theme.CyanNeon
import com.example.ui.theme.IndigoCyber
import com.example.ui.theme.VioletNeon
import com.example.util.MarkdownContent
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun MessageBubble(
    message: ChatMessageEntity,
    persona: AIPersona,
    isSpeaking: Boolean,
    onSpeakClick: () -> Unit,
    onBookmarkClick: () -> Unit,
    onDeleteClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isUser = message.role == "user"
    val context = LocalContext.current

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isUser) {
            // Assistant Avatar
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.radialGradient(
                            listOf(persona.accentColor, persona.accentColor.copy(alpha = 0.4f))
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Text(text = persona.emoji, fontSize = 18.sp)
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            modifier = Modifier.widthIn(max = 320.dp),
            horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
        ) {
            // Bubble Card
            val bubbleShape = if (isUser) {
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp)
            } else {
                RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
            }

            val bubbleBg = if (isUser) {
                Brush.linearGradient(listOf(IndigoCyber, CyanNeon.copy(alpha = 0.85f)))
            } else {
                SolidColor(MaterialTheme.colorScheme.surfaceVariant)
            }

            Box(
                modifier = Modifier
                    .clip(bubbleShape)
                    .background(bubbleBg)
                    .border(
                        width = 1.dp,
                        color = if (isUser) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = bubbleShape
                    )
                    .padding(14.dp)
            ) {
                Column {
                    // Attached image preview if user sent an image
                    if (!message.imageBase64.isNullOrBlank()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.2f))
                                .padding(bottom = 8.dp)
                        ) {
                            val imageBytes = remember(message.imageBase64) {
                                try {
                                    android.util.Base64.decode(message.imageBase64, android.util.Base64.NO_WRAP)
                                } catch (_: Exception) {
                                    null
                                }
                            }
                            if (imageBytes != null) {
                                AsyncImage(
                                    model = imageBytes,
                                    contentDescription = "Gambar terlampir",
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(8.dp))
                                )
                            }
                        }
                    }

                    if (isUser) {
                        Text(
                            text = message.content,
                            color = Color.White,
                            fontSize = 15.sp,
                            lineHeight = 22.sp
                        )
                    } else {
                        MarkdownContent(
                            text = message.content,
                            textColor = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            // Assistant Toolbar (Copy, TTS, Bookmark, Stats pill)
            if (!isUser) {
                Row(
                    modifier = Modifier.padding(top = 4.dp, start = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    // Speak button
                    IconButton(
                        onClick = onSpeakClick,
                        modifier = Modifier.size(32.dp).testTag("tts_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.VolumeUp,
                            contentDescription = "Baca Pesan",
                            tint = if (isSpeaking) CyanNeon else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Copy button
                    IconButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("Jawaban AI", message.content))
                            Toast.makeText(context, "Respon disalin", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.size(32.dp).testTag("copy_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = "Salin Respon",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Bookmark button
                    IconButton(
                        onClick = onBookmarkClick,
                        modifier = Modifier.size(32.dp).testTag("bookmark_button")
                    ) {
                        Icon(
                            imageVector = if (message.isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = "Favorit",
                            tint = if (message.isBookmarked) VioletNeon else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Delete button
                    IconButton(
                        onClick = onDeleteClick,
                        modifier = Modifier.size(32.dp).testTag("delete_message_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = "Hapus Pesan",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(15.dp)
                        )
                    }

                    // Latency / Tokens info pill
                    if (message.latencyMs != null && message.latencyMs > 0) {
                        Text(
                            text = "${message.latencyMs}ms",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun StreamingBubble(
    streamingText: String,
    persona: AIPersona,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "cursor")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "cursor_alpha"
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        // Persona Avatar
        Box(
            modifier = Modifier
                .size(36.dp)
                .clip(CircleShape)
                .background(persona.accentColor),
            contentAlignment = Alignment.Center
        ) {
            Text(text = persona.emoji, fontSize = 18.sp)
        }
        Spacer(modifier = Modifier.width(8.dp))

        Box(
            modifier = Modifier
                .widthIn(max = 320.dp)
                .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .border(
                    width = 1.dp,
                    color = CyanNeon.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 4.dp, bottomEnd = 18.dp)
                )
                .padding(14.dp)
        ) {
            Column {
                if (streamingText.isBlank()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Mengetik",
                            color = CyanNeon,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = "...",
                            color = CyanNeon,
                            fontSize = 14.sp
                        )
                    }
                } else {
                    MarkdownContent(
                        text = streamingText,
                        textColor = MaterialTheme.colorScheme.onSurface
                    )
                    // Blinking cursor
                    Box(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .size(width = 8.dp, height = 16.dp)
                            .background(CyanNeon.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun ChatInputBar(
    promptText: String,
    onPromptChange: (String) -> Unit,
    onSendClick: () -> Unit,
    onStopClick: () -> Unit,
    isGenerating: Boolean,
    attachedBitmap: Bitmap?,
    onPickImageClick: () -> Unit,
    onVoiceClick: () -> Unit,
    onRemoveImageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        // Attached image preview chip
        AnimatedVisibility(
            visible = attachedBitmap != null,
            enter = fadeIn() + scaleIn(),
            exit = fadeOut() + scaleOut()
        ) {
            if (attachedBitmap != null) {
                Row(
                    modifier = Modifier
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(
                        bitmap = attachedBitmap.asImageBitmap(),
                        contentDescription = "Pratinjau gambar",
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(6.dp)),
                        contentScale = ContentScale.Crop
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Gambar terlampir",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    IconButton(
                        onClick = onRemoveImageClick,
                        modifier = Modifier.size(28.dp).testTag("remove_image_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Hapus Gambar",
                            tint = Color.LightGray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
        }

        // Main input container
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(26.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.8f))
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(26.dp)
                )
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Pick image button
            IconButton(
                onClick = onPickImageClick,
                modifier = Modifier.size(40.dp).testTag("pick_image_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = "Kirim Gambar",
                    tint = CyanNeon,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Voice input button
            IconButton(
                onClick = onVoiceClick,
                modifier = Modifier.size(40.dp).testTag("voice_input_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Mic,
                    contentDescription = "Input Suara",
                    tint = VioletNeon,
                    modifier = Modifier.size(20.dp)
                )
            }

            // Text field
            BasicTextField(
                value = promptText,
                onValueChange = onPromptChange,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp, vertical = 8.dp)
                    .testTag("chat_input_field"),
                textStyle = TextStyle(
                    fontSize = 15.sp,
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = SolidColor(CyanNeon),
                decorationBox = { innerTextField ->
                    if (promptText.isEmpty()) {
                        Text(
                            text = "Tanyakan apapun pada Aura AI...",
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                        )
                    }
                    innerTextField()
                }
            )

            // Send or Stop button
            if (isGenerating) {
                IconButton(
                    onClick = onStopClick,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFFEF4444))
                        .testTag("stop_generating_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Stop,
                        contentDescription = "Hentikan",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            } else {
                val canSend = promptText.isNotBlank() || attachedBitmap != null
                IconButton(
                    onClick = onSendClick,
                    enabled = canSend,
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(
                            if (canSend) Brush.linearGradient(listOf(CyanNeon, IndigoCyber))
                            else SolidColor(Color.DarkGray)
                        )
                        .testTag("send_message_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Send,
                        contentDescription = "Kirim",
                        tint = if (canSend) Color.White else Color.Gray,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun EmptyChatSuggestions(
    persona: AIPersona,
    onSuggestionClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val suggestions = when (persona.id) {
        "hexa_coder" -> listOf(
            "💻 Tulis fungsi debounce di Kotlin Coroutines",
            "⚡ Optimasi layout Compose agar 60fps tanpa recomposition",
            "🛡️ Bagaimana cara membuat arsitektur Clean Architecture di Android?",
            "🐞 Debug: Kenapa Room database lambat di main thread?"
        )
        "lyra_writer" -> listOf(
            "✍️ Buat copy iklan kreatif untuk aplikasi startup",
            "📖 Tulis cerpen sci-fi tentang AI yang punya mimpi",
            "🌐 Terjemahkan teks formal ini ke bahasa Inggris yang elegan",
            "📝 Perbaiki struktur dan tata bahasa artikel ini"
        )
        "sage_tutor" -> listOf(
            "🧠 Jelaskan konsep Quantum Computing seperti ke anak 10 tahun",
            "⚛️ Bagaimana cara kerja Transformer model di AI?",
            "📊 Jelaskan rumus probabilitas Bayes dengan contoh dadu",
            "🌌 Mengapa langit berwarna biru saat siang hari?"
        )
        "echo_friend" -> listOf(
            "🤝 Capek banget hari ini, boleh curhat?",
            "☕ Kasih motivasi simpel buat mulai hari ini dong",
            "🎵 Rekomendasi kegiatan seru buat refreshing di akhir pekan",
            "💭 Gimana caranya mengatasi overthinking di malam hari?"
        )
        else -> listOf(
            "💡 Ide kreatif untuk memulai proyek aplikasi baru",
            "⚡ Trik jitu bikin HP kentang terasa lebih kencang & hemat baterai",
            "📝 Ringkas artikel panjang menjadi 3 poin penting",
            "🚀 Bantu susun rencana belajar programming dari nol"
        )
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Hero Graphic Card
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(16.dp))
                .border(
                    1.dp,
                    Brush.horizontalGradient(listOf(CyanNeon.copy(alpha = 0.4f), VioletNeon.copy(alpha = 0.4f))),
                    RoundedCornerShape(16.dp)
                )
        ) {
            Image(
                painter = painterResource(id = R.drawable.aura_hero_banner_1791686744482),
                contentDescription = "Aura AI Hero Banner",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().height(130.dp)
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(130.dp)
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color(0xCC0A0E17))
                        )
                    )
            )
            Column(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(12.dp)
            ) {
                Text(
                    text = "${persona.emoji} ${persona.name}",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = persona.tagline,
                    fontSize = 12.sp,
                    color = CyanNeon
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "Mulai obrolan dengan saran berikut:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            suggestions.forEach { prompt ->
                Card(
                    onClick = { onSuggestionClick(prompt) },
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    ),
                    border = CardDefaults.outlinedCardBorder(),
                    modifier = Modifier.fillMaxWidth().testTag("suggestion_chip")
                ) {
                    Text(
                        text = prompt,
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}
