package com.health.friday.memory.rag

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.text.textembedder.TextEmbedder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

open class FridayEmbedder(private val context: Context) {
    private var embedder: TextEmbedder? = null
    private val modelName = "universal_sentence_encoder.tflite"

    open suspend fun initialize() = withContext(Dispatchers.IO) {
        if (embedder != null) return@withContext
        val modelFile = File(context.getExternalFilesDir(null), modelName)
        if (!modelFile.exists()) {
            Log.e("FRIDAY_RAG", "Embedding model not found: ${modelFile.absolutePath}")
            return@withContext
        }
        try {
            val baseOptions = BaseOptions.builder().setModelAssetPath(modelFile.absolutePath).build()
            val options = TextEmbedder.TextEmbedderOptions.builder()
                .setBaseOptions(baseOptions)
                .build()
            embedder = TextEmbedder.createFromOptions(context, options)
            Log.i("FRIDAY_RAG", "TextEmbedder initialized successfully.")
        } catch (e: Exception) {
            Log.e("FRIDAY_RAG", "Embedder init failed: ${e.message}")
        }
    }

    open fun embed(text: String): FloatArray? {
        val result = embedder?.embed(text) ?: return null
        return result.embeddingResult().embeddings().firstOrNull()?.floatEmbedding()
    }
}