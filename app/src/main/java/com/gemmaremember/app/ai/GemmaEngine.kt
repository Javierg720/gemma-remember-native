package com.gemmaremember.app.ai

import android.content.Context
import com.google.ai.edge.litertlm.Engine
import com.google.ai.edge.litertlm.EngineConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaEngine(private val context: Context) {

    private var engine: Engine? = null
    private var isReady = false

    companion object {
        const val MODEL_FILENAME = "gemma4-e2b.litertlm"
        const val MODEL_URL =
            "https://huggingface.co/litert-community/gemma-4-E2B-it-litert-lm/" +
            "resolve/main/gemma-4-E2B-it.litertlm"
    }

    fun isModelDownloaded(): Boolean =
        File(context.filesDir, MODEL_FILENAME).exists()

    suspend fun downloadModel(onProgress: (Int) -> Unit) = withContext(Dispatchers.IO) {
        val dest = File(context.filesDir, MODEL_FILENAME)
        val conn = java.net.URL(MODEL_URL).openConnection()
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
    }

    suspend fun initialize() = withContext(Dispatchers.IO) {
        if (isReady) return@withContext
        val modelPath = File(context.filesDir, MODEL_FILENAME).absolutePath
        val config = EngineConfig(modelPath = modelPath)
        engine = Engine(config).also { it.initialize() }
        isReady = true
    }

    suspend fun generate(prompt: String): String = withContext(Dispatchers.Default) {
        val eng = engine ?: throw IllegalStateException("Engine not initialized")
        eng.createConversation().use { conversation ->
            val result = conversation.sendMessage(prompt)
            result.contents.toString()
        }
    }

    fun isInitialized() = isReady
}
