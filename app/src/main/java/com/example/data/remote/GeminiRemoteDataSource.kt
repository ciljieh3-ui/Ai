package com.example.data.remote

import android.graphics.Bitmap
import android.util.Base64
import com.example.data.model.AIPersona
import com.example.data.model.PersonaPresets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.ByteArrayOutputStream
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

data class GeminiMessagePayload(
    val role: String, // "user" or "model"
    val text: String,
    val imageBase64: String? = null
)

class GeminiRemoteDataSource(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .retryOnConnectionFailure(true)
        .build()
) {
    companion object {
        private const val BASE_URL = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    /**
     * Streams response chunks via SSE (Server-Sent Events) from Gemini API.
     * Emits String chunks as they arrive.
     */
    fun streamGenerateContent(
        apiKey: String,
        modelName: String,
        persona: AIPersona,
        history: List<GeminiMessagePayload>,
        temperature: Float = 0.7f
    ): Flow<String> = flow {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key belum disetel. Masukkan API Key Google AI Studio Anda di menu Pengaturan / Kunci API.")
        }

        val url = "$BASE_URL/$modelName:streamGenerateContent?alt=sse&key=$apiKey"
        val requestJson = buildRequestJson(persona, history, temperature)
        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        val response = client.newCall(request).execute()
        if (!response.isSuccessful) {
            val errorBody = response.body?.string().orEmpty()
            val parsedError = try {
                val errObj = JSONObject(errorBody).optJSONObject("error")
                errObj?.optString("message") ?: errorBody
            } catch (_: Exception) {
                errorBody
            }
            throw Exception("Gemini Error (${response.code}): $parsedError")
        }

        val inputStream = response.body?.byteStream()
            ?: throw Exception("Tidak ada respon data dari Gemini server")

        BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                val currentLine = line?.trim().orEmpty()
                if (currentLine.startsWith("data:")) {
                    val jsonStr = currentLine.removePrefix("data:").trim()
                    if (jsonStr.isNotEmpty() && jsonStr != "[DONE]") {
                        val textChunk = extractTextFromJson(jsonStr)
                        if (textChunk.isNotEmpty()) {
                            emit(textChunk)
                        }
                    }
                }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Non-streaming direct generation for quick testing or fallback.
     */
    suspend fun generateContent(
        apiKey: String,
        modelName: String,
        persona: AIPersona,
        history: List<GeminiMessagePayload>,
        temperature: Float = 0.7f
    ): String {
        if (apiKey.isBlank()) {
            throw IllegalArgumentException("API Key belum disetel. Masukkan API Key Google AI Studio Anda.")
        }

        val url = "$BASE_URL/$modelName:generateContent?key=$apiKey"
        val requestJson = buildRequestJson(persona, history, temperature)
        val body = requestJson.toString().toRequestBody("application/json; charset=utf-8".toMediaType())

        val request = Request.Builder()
            .url(url)
            .post(body)
            .build()

        return kotlinx.coroutines.withContext(Dispatchers.IO) {
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) {
                val errorBody = response.body?.string().orEmpty()
                val parsedError = try {
                    val errObj = JSONObject(errorBody).optJSONObject("error")
                    errObj?.optString("message") ?: errorBody
                } catch (_: Exception) {
                    errorBody
                }
                throw Exception("Gemini Error (${response.code}): $parsedError")
            }

            val bodyString = response.body?.string().orEmpty()
            extractTextFromJson(bodyString)
        }
    }

    /**
     * Test connection to verify if the API key is active.
     */
    suspend fun testApiKeyConnection(apiKey: String): Boolean {
        if (apiKey.isBlank()) return false
        return try {
            val res = generateContent(
                apiKey = apiKey,
                modelName = "gemini-3.5-flash",
                persona = PersonaPresets.defaultPersona,
                history = listOf(GeminiMessagePayload("user", "Ping. Jawab 'OK' saja.")),
                temperature = 0.1f
            )
            res.isNotBlank()
        } catch (_: Exception) {
            false
        }
    }

    private fun buildRequestJson(
        persona: AIPersona,
        history: List<GeminiMessagePayload>,
        temperature: Float
    ): JSONObject {
        val root = JSONObject()

        // System Instruction
        if (persona.systemPrompt.isNotBlank()) {
            val systemInstruction = JSONObject()
            val sysParts = JSONArray()
            val part = JSONObject()
            part.put("text", persona.systemPrompt)
            sysParts.put(part)
            systemInstruction.put("parts", sysParts)
            root.put("systemInstruction", systemInstruction)
        }

        // Contents
        val contentsArray = JSONArray()
        for (item in history) {
            val contentObj = JSONObject()
            contentObj.put("role", if (item.role == "assistant") "model" else "user")

            val partsArray = JSONArray()

            // Optional image part
            if (!item.imageBase64.isNullOrBlank()) {
                val imagePart = JSONObject()
                val inlineData = JSONObject()
                inlineData.put("mimeType", "image/jpeg")
                inlineData.put("data", item.imageBase64)
                imagePart.put("inlineData", inlineData)
                partsArray.put(imagePart)
            }

            // Text part
            val textPart = JSONObject()
            textPart.put("text", item.text)
            partsArray.put(textPart)

            contentObj.put("parts", partsArray)
            contentsArray.put(contentObj)
        }
        root.put("contents", contentsArray)

        // Generation Config
        val genConfig = JSONObject()
        genConfig.put("temperature", temperature)
        root.put("generationConfig", genConfig)

        return root
    }

    private fun extractTextFromJson(jsonString: String): String {
        return try {
            val json = JSONObject(jsonString)
            val candidates = json.optJSONArray("candidates") ?: return ""
            if (candidates.length() == 0) return ""
            val firstCandidate = candidates.getJSONObject(0)
            val content = firstCandidate.optJSONObject("content") ?: return ""
            val parts = content.optJSONArray("parts") ?: return ""
            val sb = StringBuilder()
            for (i in 0 until parts.length()) {
                val part = parts.getJSONObject(i)
                val text = part.optString("text", "")
                sb.append(text)
            }
            sb.toString()
        } catch (_: Exception) {
            ""
        }
    }
}

fun Bitmap.toBase64Jpeg(): String {
    val outputStream = ByteArrayOutputStream()
    compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
    return Base64.encodeToString(outputStream.toByteArray(), Base64.NO_WRAP)
}
