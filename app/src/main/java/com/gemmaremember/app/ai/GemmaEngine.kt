package com.gemmaremember.app.ai

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

enum class AiMode { API, LOCAL }

class GemmaEngine(private val context: Context) {

    private var engine: Any? = null // LiteRT Engine, loaded via reflection
    private var isLocalReady = false
    private var mode: AiMode = AiMode.API
    private var apiKey: String = ""

    private val prefs = context.getSharedPreferences("gemma_prefs", 0)

    companion object {
        const val MODEL_FILENAME = "gemma4-e2b.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
            "resolve/main/gemma-4-E2B-it.litertlm"
        const val API_MODEL = "gemma-4-26b-a4b-it"
    }

    init {
        apiKey = prefs.getString("apiKey", "") ?: ""
        mode = if (prefs.getString("aiMode", "api") == "local") AiMode.LOCAL else AiMode.API
    }

    fun getMode() = mode
    fun getApiKey() = apiKey
    fun isReady() = mode == AiMode.API && apiKey.isNotBlank() || mode == AiMode.LOCAL && isLocalReady

    fun setApiMode(key: String) {
        apiKey = key
        mode = AiMode.API
        prefs.edit().putString("apiKey", key).putString("aiMode", "api").apply()
    }

    fun setLocalMode() {
        mode = AiMode.LOCAL
        prefs.edit().putString("aiMode", "local").apply()
    }

    fun isModelDownloaded(): Boolean =
        File(context.filesDir, MODEL_FILENAME).exists()

    suspend fun downloadModel(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val dest = File(context.filesDir, MODEL_FILENAME)
        val conn = URL(MODEL_URL).openConnection()
        conn.setRequestProperty("User-Agent", "GemmaRemember/3.0")
        val total = conn.contentLengthLong
        conn.getInputStream().use { input ->
            java.io.FileOutputStream(dest).use { output ->
                val buf = ByteArray(65536)
                var downloaded = 0L
                var n: Int
                while (input.read(buf).also { n = it } >= 0) {
                    output.write(buf, 0, n)
                    downloaded += n
                    if (total > 0) onProgress((downloaded * 100 / total).toInt())
                }
            }
        }
        setLocalMode()
    }

    suspend fun initializeLocal() = withContext(Dispatchers.IO) {
        if (isLocalReady) return@withContext
        try {
            val engineClass = Class.forName("com.google.ai.edge.litertlm.Engine")
            val configClass = Class.forName("com.google.ai.edge.litertlm.EngineConfig")
            val modelPath = File(context.filesDir, MODEL_FILENAME).absolutePath
            val config = configClass.getConstructor(String::class.java).newInstance(modelPath)
            val eng = engineClass.getConstructor(configClass).newInstance(config)
            engineClass.getMethod("initialize").invoke(eng)
            engine = eng
            isLocalReady = true
        } catch (e: Exception) {
            isLocalReady = false
            throw e
        }
    }

    suspend fun generate(prompt: String): String {
        // Try primary mode first, fallback to other
        return when (mode) {
            AiMode.API -> {
                try {
                    generateViaApi(prompt)
                } catch (e: Exception) {
                    if (isLocalReady) generateLocal(prompt)
                    else throw e
                }
            }
            AiMode.LOCAL -> {
                try {
                    if (isLocalReady) generateLocal(prompt)
                    else if (apiKey.isNotBlank()) generateViaApi(prompt)
                    else throw IllegalStateException("No AI backend available")
                } catch (e: Exception) {
                    if (apiKey.isNotBlank()) generateViaApi(prompt)
                    else throw e
                }
            }
        }
    }

    private suspend fun generateViaApi(prompt: String): String = withContext(Dispatchers.IO) {
        val url = URL("https://generativelanguage.googleapis.com/v1beta/models/$API_MODEL:generateContent?key=$apiKey")
        val conn = url.openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.setRequestProperty("Content-Type", "application/json")
        conn.doOutput = true
        conn.connectTimeout = 30000
        conn.readTimeout = 60000

        val body = JSONObject().apply {
            put("contents", JSONArray().put(JSONObject().apply {
                put("parts", JSONArray().put(JSONObject().apply {
                    put("text", prompt)
                }))
            }))
            put("generationConfig", JSONObject().apply {
                put("maxOutputTokens", 500)
                put("temperature", 0.7)
            })
        }

        conn.outputStream.use { it.write(body.toString().toByteArray()) }

        if (conn.responseCode != 200) {
            val error = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
            throw Exception("API error ${conn.responseCode}: $error")
        }

        val response = conn.inputStream.bufferedReader().readText()
        val json = JSONObject(response)
        val parts = json.getJSONArray("candidates")
            .getJSONObject(0)
            .getJSONObject("content")
            .getJSONArray("parts")

        // Filter out thought parts, get text
        val sb = StringBuilder()
        for (i in 0 until parts.length()) {
            val part = parts.getJSONObject(i)
            if (!part.optBoolean("thought", false) && part.has("text")) {
                sb.append(part.getString("text"))
            }
        }
        sb.toString()
    }

    private suspend fun generateLocal(prompt: String): String = withContext(Dispatchers.Default) {
        val eng = engine ?: throw IllegalStateException("Local engine not initialized")
        val convMethod = eng.javaClass.getMethod("createConversation")
        val conversation = convMethod.invoke(eng)!!
        try {
            val sendMethod = conversation.javaClass.methods.find {
                it.name == "sendMessage" && it.parameterTypes.size == 2 && it.parameterTypes[0] == String::class.java
            } ?: throw Exception("sendMessage not found")
            val result = sendMethod.invoke(conversation, prompt, emptyMap<String, Any>())
            result?.let {
                val contents = it.javaClass.getMethod("getContents").invoke(it)
                contents?.toString() ?: ""
            } ?: ""
        } finally {
            try { conversation.javaClass.getMethod("close").invoke(conversation) } catch (_: Exception) {}
        }
    }
}
