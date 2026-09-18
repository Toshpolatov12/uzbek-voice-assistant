package uz.voiceassistant.wakeword

import android.content.Context
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.tensorflow.lite.Interpreter
import java.io.FileInputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel

/**
 * WakeWordDetector runs self-hosted openWakeWord acoustic detection on-device using TFLite.
 * It streams audio from AudioRecord (16kHz, 16-bit PCM Mono) and feeds sliding window
 * frames into the TensorFlow Lite inference engine.
 */
class WakeWordDetector(
    private val context: Context,
    private val onWakeWordDetected: () -> Unit
) {
    private val tag = "WakeWordDetector"
    private var interpreter: Interpreter? = null
    private var audioRecord: AudioRecord? = null
    private var recordingJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.Default)

    private val sampleRate = 16000
    private val channelConfig = AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat).coerceAtLeast(4096)

    // openWakeWord typically accepts chunks of 1280 samples (80ms at 16kHz)
    private val chunkSize = 1280
    private val threshold = 0.5f

    @Volatile
    private var isRunning = false

    init {
        loadModel()
    }

    private fun loadModel() {
        try {
            val assetFileDescriptor = context.assets.openFd("wakeword/uzbek_wakeword.tflite")
            val fileInputStream = FileInputStream(assetFileDescriptor.fileDescriptor)
            val fileChannel = fileInputStream.channel
            val startOffset = assetFileDescriptor.startOffset
            val declaredLength = assetFileDescriptor.declaredLength
            val modelBuffer = fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)

            val options = Interpreter.Options().apply {
                setNumThreads(2)
            }
            interpreter = Interpreter(modelBuffer, options)
            Log.i(tag, "openWakeWord TFLite model loaded successfully.")
        } catch (e: Exception) {
            Log.w(tag, "Could not load openWakeWord TFLite model from assets (using fallback acoustic monitor): ${e.message}")
            interpreter = null
        }
    }

    fun start() {
        if (isRunning) return
        isRunning = true

        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(tag, "AudioRecord initialization failed")
                stop()
                return
            }

            audioRecord?.startRecording()
        } catch (e: SecurityException) {
            Log.e(tag, "RECORD_AUDIO permission missing", e)
            stop()
            return
        }

        recordingJob = scope.launch {
            val audioBuffer = ShortArray(chunkSize)
            val inputByteBuffer = ByteBuffer.allocateDirect(chunkSize * 4).apply {
                order(ByteOrder.nativeOrder())
            }
            val outputScores = Array(1) { FloatArray(1) }

            while (isActive && isRunning) {
                val readCount = audioRecord?.read(audioBuffer, 0, chunkSize) ?: -1
                if (readCount > 0) {
                    if (interpreter != null) {
                        // Normalize 16-bit PCM to [-1.0, 1.0] float for model input
                        inputByteBuffer.rewind()
                        for (i in 0 until readCount) {
                            inputByteBuffer.putFloat(audioBuffer[i] / 32768.0f)
                        }

                        try {
                            interpreter?.run(inputByteBuffer, outputScores)
                            val score = outputScores[0][0]
                            if (score >= threshold) {
                                Log.i(tag, "Wake word detected with score: $score")
                                onWakeWordDetected()
                            }
                        } catch (e: Exception) {
                            Log.e(tag, "Inference error", e)
                        }
                    } else {
                        // Fallback: Energy-based voice activity monitor if model not yet trained
                        var sum = 0.0
                        for (i in 0 until readCount) {
                            sum += audioBuffer[i] * audioBuffer[i]
                        }
                        val rms = Math.sqrt(sum / readCount)
                        if (rms > 12000.0) { // Loud deliberate speech cue
                            Log.d(tag, "Voice energy threshold reached: $rms")
                        }
                    }
                }
            }
        }
    }

    fun stop() {
        isRunning = false
        recordingJob?.cancel()
        recordingJob = null

        try {
            if (audioRecord?.recordingState == AudioRecord.RECORDSTATE_RECORDING) {
                audioRecord?.stop()
            }
            audioRecord?.release()
        } catch (e: Exception) {
            Log.e(tag, "Error stopping AudioRecord", e)
        }
        audioRecord = null
    }

    fun release() {
        stop()
        interpreter?.close()
        interpreter = null
    }
}
