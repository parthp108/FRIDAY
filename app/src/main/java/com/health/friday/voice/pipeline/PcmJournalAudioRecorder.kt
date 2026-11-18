package com.health.friday.voice.pipeline

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.sqrt

class PcmJournalAudioRecorder(private val context: Context) {

    companion object {
        private const val TAG = "PcmJournalRecorder"
        private const val SAMPLE_RATE = 16000
    }

    private var audioRecord: AudioRecord? = null
    private val isRecording = AtomicBoolean(false)
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var outputFile: File? = null

    private val _recordingDurationSec = MutableStateFlow(0)
    val recordingDurationSec: StateFlow<Int> = _recordingDurationSec.asStateFlow()

    private val _rmsDb = MutableStateFlow(0f)
    val rmsDb: StateFlow<Float> = _rmsDb.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startRecording(): File? {
        if (isRecording.getAndSet(true)) return outputFile

        val file = File(context.cacheDir, "journal_venting_${System.currentTimeMillis()}.wav")
        outputFile = file

        val minBuffer = AudioRecord.getMinBufferSize(
            SAMPLE_RATE,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_16BIT
        )
        val bufferSize = maxOf(minBuffer * 4, 8192)

        runCatching {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT,
                bufferSize
            )
            audioRecord?.startRecording()
        }.onFailure {
            Log.e(TAG, "Failed to start AudioRecord for journaling", it)
            isRecording.set(false)
            return null
        }

        scope.launch {
            val shortBuffer = ShortArray(512)
            val byteBuffer = ByteArray(1024)
            val fos = FileOutputStream(file)

            writeWavHeader(fos, SAMPLE_RATE, 1, 16, 0)

            var totalBytesWritten = 0L
            val startTime = System.currentTimeMillis()

            while (isRecording.get()) {
                val readShorts = audioRecord?.read(shortBuffer, 0, shortBuffer.size, AudioRecord.READ_BLOCKING) ?: 0
                if (readShorts > 0) {
                    for (i in 0 until readShorts) {
                        val sample = shortBuffer[i].toInt()
                        byteBuffer[i * 2] = (sample and 0xFF).toByte()
                        byteBuffer[i * 2 + 1] = ((sample ushr 8) and 0xFF).toByte()
                    }
                    val bytesToWrite = readShorts * 2
                    fos.write(byteBuffer, 0, bytesToWrite)
                    totalBytesWritten += bytesToWrite

                    _recordingDurationSec.value = ((System.currentTimeMillis() - startTime) / 1000).toInt()

                    var sum = 0.0
                    for (i in 0 until readShorts) {
                        val s = shortBuffer[i].toDouble()
                        sum += s * s
                    }
                    val rms = sqrt(sum / readShorts)
                    _rmsDb.value = (rms.toFloat() / 300.0f).coerceIn(0f, 1f)
                }
            }

            fos.flush()
            fos.close()

            updateWavHeader(file, totalBytesWritten)
            Log.i(TAG, "Completed continuous PCM journal recording: ${file.length()} bytes (${_recordingDurationSec.value}s)")
        }

        return file
    }

    fun stopRecording(): File? {
        if (!isRecording.getAndSet(false)) return outputFile
        runCatching {
            audioRecord?.stop()
            audioRecord?.release()
        }
        audioRecord = null
        return outputFile
    }

    private fun writeWavHeader(
        out: FileOutputStream,
        sampleRate: Int,
        channels: Int,
        bitsPerSample: Int,
        pcmDataLength: Long
    ) {
        val totalDataLen = pcmDataLength + 36
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val header = ByteArray(44)

        header[0] = 'R'.code.toByte(); header[1] = 'I'.code.toByte(); header[2] = 'F'.code.toByte(); header[3] = 'F'.code.toByte()
        header[4] = (totalDataLen and 0xff).toByte()
        header[5] = (totalDataLen shr 8 and 0xff).toByte()
        header[6] = (totalDataLen shr 16 and 0xff).toByte()
        header[7] = (totalDataLen shr 24 and 0xff).toByte()
        header[8] = 'W'.code.toByte(); header[9] = 'A'.code.toByte(); header[10] = 'V'.code.toByte(); header[11] = 'E'.code.toByte()
        header[12] = 'f'.code.toByte(); header[13] = 'm'.code.toByte(); header[14] = 't'.code.toByte(); header[15] = ' '.code.toByte()
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0
        header[20] = 1; header[21] = 0
        header[22] = channels.toByte(); header[23] = 0
        header[24] = (sampleRate and 0xff).toByte()
        header[25] = (sampleRate shr 8 and 0xff).toByte()
        header[26] = (sampleRate shr 16 and 0xff).toByte()
        header[27] = (sampleRate shr 24 and 0xff).toByte()
        header[28] = (byteRate and 0xff).toByte()
        header[29] = (byteRate shr 8 and 0xff).toByte()
        header[30] = (byteRate shr 16 and 0xff).toByte()
        header[31] = (byteRate shr 24 and 0xff).toByte()
        header[32] = (channels * bitsPerSample / 8).toByte(); header[33] = 0
        header[34] = bitsPerSample.toByte(); header[35] = 0
        header[36] = 'd'.code.toByte(); header[37] = 'a'.code.toByte(); header[38] = 't'.code.toByte(); header[39] = 'a'.code.toByte()
        header[40] = (pcmDataLength and 0xff).toByte()
        header[41] = (pcmDataLength shr 8 and 0xff).toByte()
        header[42] = (pcmDataLength shr 16 and 0xff).toByte()
        header[43] = (pcmDataLength shr 24 and 0xff).toByte()

        out.write(header, 0, 44)
    }

    private fun updateWavHeader(wavFile: File, pcmDataLength: Long) {
        val totalDataLen = pcmDataLength + 36
        RandomAccessFile(wavFile, "rw").use { raf ->
            raf.seek(4)
            raf.write(intToByteArray(totalDataLen.toInt()))
            raf.seek(40)
            raf.write(intToByteArray(pcmDataLength.toInt()))
        }
    }

    private fun intToByteArray(value: Int): ByteArray {
        return byteArrayOf(
            (value and 0xff).toByte(),
            (value shr 8 and 0xff).toByte(),
            (value shr 16 and 0xff).toByte(),
            (value shr 24 and 0xff).toByte()
        )
    }
}
