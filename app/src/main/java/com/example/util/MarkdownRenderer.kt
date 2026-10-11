package com.example.util

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyanNeon

sealed class MarkdownElement {
    data class Paragraph(val text: String) : MarkdownElement()
    data class CodeBlock(val language: String, val code: String) : MarkdownElement()
    data class Header(val level: Int, val text: String) : MarkdownElement()
    data class BulletItem(val text: String) : MarkdownElement()
    data class Quote(val text: String) : MarkdownElement()
}

@Composable
fun MarkdownContent(
    text: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface
) {
    val elements = remember(text) { parseMarkdown(text) }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        elements.forEach { element ->
            when (element) {
                is MarkdownElement.Paragraph -> {
                    Text(
                        text = formatInlineMarkdown(element.text, textColor),
                        fontSize = 15.sp,
                        lineHeight = 22.sp,
                        color = textColor
                    )
                }
                is MarkdownElement.Header -> {
                    val fontSize = when (element.level) {
                        1 -> 20.sp
                        2 -> 18.sp
                        else -> 16.sp
                    }
                    Text(
                        text = element.text,
                        fontSize = fontSize,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                is MarkdownElement.BulletItem -> {
                    Row(
                        modifier = Modifier.padding(start = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("•", color = CyanNeon, fontWeight = FontWeight.Bold)
                        Text(
                            text = formatInlineMarkdown(element.text, textColor),
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = textColor
                        )
                    }
                }
                is MarkdownElement.Quote -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Text(
                            text = formatInlineMarkdown(element.text, textColor),
                            fontSize = 14.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                is MarkdownElement.CodeBlock -> {
                    CodeBlockView(language = element.language, code = element.code)
                }
            }
        }
    }
}

@Composable
fun CodeBlockView(language: String, code: String) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(Color(0xFF0F172A))
    ) {
        // Code Block Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFF1E293B))
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = language.ifBlank { "code" }.uppercase(),
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                color = CyanNeon
            )
            IconButton(
                onClick = {
                    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    clipboard.setPrimaryClip(ClipData.newPlainText("Code", code))
                    Toast.makeText(context, "Kode disalin ke clipboard", Toast.LENGTH_SHORT).show()
                }
            ) {
                Icon(
                    imageVector = Icons.Default.ContentCopy,
                    contentDescription = "Salin Kode",
                    tint = Color.LightGray
                )
            }
        }

        // Code Content (horizontally scrollable without wrapping)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(scrollState)
                .padding(12.dp)
        ) {
            Text(
                text = code,
                fontFamily = FontFamily.Monospace,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                color = Color(0xFFE2E8F0)
            )
        }
    }
}

private fun parseMarkdown(raw: String): List<MarkdownElement> {
    val elements = mutableListOf<MarkdownElement>()
    val lines = raw.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code Block check
        if (line.trimStart().startsWith("```")) {
            val language = line.trim().removePrefix("```").trim()
            val codeBuilder = StringBuilder()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeBuilder.appendLine(lines[i])
                i++
            }
            elements.add(MarkdownElement.CodeBlock(language, codeBuilder.toString().trimEnd()))
            i++
            continue
        }

        // Headers
        if (line.startsWith("#")) {
            val level = line.takeWhile { it == '#' }.length
            val headerText = line.drop(level).trim()
            elements.add(MarkdownElement.Header(level, headerText))
            i++
            continue
        }

        // Bullet list
        if (line.trimStart().startsWith("- ") || line.trimStart().startsWith("* ") || line.trimStart().startsWith("• ")) {
            val itemText = line.trimStart().drop(2).trim()
            elements.add(MarkdownElement.BulletItem(itemText))
            i++
            continue
        }

        // Quote
        if (line.trimStart().startsWith("> ")) {
            val quoteText = line.trimStart().drop(2).trim()
            elements.add(MarkdownElement.Quote(quoteText))
            i++
            continue
        }

        // Regular text
        if (line.isNotBlank()) {
            elements.add(MarkdownElement.Paragraph(line.trim()))
        }
        i++
    }
    return elements
}

private fun formatInlineMarkdown(text: String, defaultColor: Color) = buildAnnotatedString {
    var cursor = 0
    val boldPattern = Regex("\\*\\*(.*?)\\*\\*")
    val matches = boldPattern.findAll(text).toList()

    for (match in matches) {
        if (match.range.first > cursor) {
            append(text.substring(cursor, match.range.first))
        }
        pushStyle(SpanStyle(fontWeight = FontWeight.Bold, color = CyanNeon))
        append(match.groupValues[1])
        pop()
        cursor = match.range.last + 1
    }
    if (cursor < text.length) {
        append(text.substring(cursor))
    }
}
