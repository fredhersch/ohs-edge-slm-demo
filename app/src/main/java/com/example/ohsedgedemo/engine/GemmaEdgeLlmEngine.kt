package com.example.ohsedgedemo.engine

import android.content.Context
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class GemmaEdgeLlmEngine(private val context: Context) {

    private var inferenceInstance: LlmInference? = null
    var isModelLoaded: Boolean = false
        private set

    var loadedModelPath: String = ""
        private set

    fun getModelFile(): File? {
        val candidates = listOf(
            File(context.filesDir, "model.bin"),
            File(context.filesDir, "model.task"),
            File(context.filesDir, "model.litertlm"),
            File("/data/local/tmp/gemma-4-E2B-it-gpu.litertlm")
        )
        return candidates.firstOrNull { it.exists() && it.length() > 100_000_000L }
    }

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        val file = getModelFile() ?: return@withContext false
        return@withContext try {
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(file.absolutePath)
                .setMaxTokens(512)
                .build()
            inferenceInstance = LlmInference.createFromOptions(context, options)
            isModelLoaded = true
            loadedModelPath = file.absolutePath
            true
        } catch (e: Throwable) {
            e.printStackTrace()
            isModelLoaded = false
            false
        }
    }

    suspend fun generateResponse(prompt: String): String = withContext(Dispatchers.Default) {
        val instance = inferenceInstance ?: throw IllegalStateException("Model not loaded")
        instance.generateResponse(prompt)
    }
}
