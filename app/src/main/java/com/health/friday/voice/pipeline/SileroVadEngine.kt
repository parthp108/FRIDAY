package com.health.friday.voice.pipeline

import android.content.Context
import ai.onnxruntime.OnnxTensor
import ai.onnxruntime.OrtEnvironment
import ai.onnxruntime.OrtSession
import java.nio.FloatBuffer
import kotlin.math.abs

class SileroVadEngine(private val context: Context) {
    private var env: OrtEnvironment? = null
    private var session: OrtSession? = null

    private var hTensor: OnnxTensor? = null
    private var cTensor: OnnxTensor? = null

    init {
        initOnnx()
    }

    private fun initOnnx() {
        runCatching {
            env = OrtEnvironment.getEnvironment()
            val modelBytes = context.assets.open("silero_vad.onnx").readBytes()
            session = env?.createSession(modelBytes)
            resetStates()
        }
    }

    private fun resetStates() {
        runCatching {
            val envRef = env ?: return
            val zeros = FloatArray(2 * 1 * 64) { 0f }
            hTensor = OnnxTensor.createTensor(envRef, FloatBuffer.wrap(zeros), longArrayOf(2, 1, 64))
            cTensor = OnnxTensor.createTensor(envRef, FloatBuffer.wrap(zeros), longArrayOf(2, 1, 64))
        }
    }

    fun evaluate(pcm16Data: ShortArray): Float {
        val sess = session
        val envRef = env
        if (sess == null || envRef == null) {
            // Fallback energy-based probability if ONNX asset is not present
            var maxVal = 0
            for (sample in pcm16Data) {
                val absVal = abs(sample.toInt())
                if (absVal > maxVal) maxVal = absVal
            }
            return (maxVal / 32768.0f * 3f).coerceIn(0f, 1f)
        }

        return runCatching {
            val floatData = FloatArray(pcm16Data.size) { pcm16Data[it] / 32768.0f }
            val inputTensor = OnnxTensor.createTensor(envRef, FloatBuffer.wrap(floatData), longArrayOf(1, floatData.size.toLong()))
            val sampleRateTensor = OnnxTensor.createTensor(envRef, intArrayOf(16000))

            val inputs = mutableMapOf<String, OnnxTensor>()
            inputs["input"] = inputTensor
            inputs["sr"] = sampleRateTensor
            hTensor?.let { inputs["h"] = it }
            cTensor?.let { inputs["c"] = it }

            val results = sess.run(inputs)
            val prob = ((results[0].value as Array<*>)[0] as FloatArray)[0]

            if (results.size() >= 3) {
                hTensor = results[1] as? OnnxTensor
                cTensor = results[2] as? OnnxTensor
            }

            inputTensor.close()
            sampleRateTensor.close()
            results.close()

            prob
        }.getOrDefault(0f)
    }
}
