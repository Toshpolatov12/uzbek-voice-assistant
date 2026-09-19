package uz.voiceassistant.speech

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class AudioRecorderManager(private val context: Context) {

    private val tag = "AudioRecorderManager"
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    var isRecording = false
        private set

    fun startRecording(): Boolean {
        if (isRecording) return true

        try {
            val audioDir = File(context.cacheDir, "audio").apply { mkdirs() }
            outputFile = File(audioDir, "user_speech_${System.currentTimeMillis()}.m4a")

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64000)
                setAudioSamplingRate(16000)
                setOutputFile(outputFile?.absolutePath)
                prepare()
                start()
            }

            isRecording = true
            Log.i(tag, "Audio recording started successfully: ${outputFile?.absolutePath}")
            return true
        } catch (e: Exception) {
            Log.e(tag, "Failed to start AudioRecorder", e)
            stopRecording()
            return false
        }
    }

    fun stopRecording(): ByteArray? {
        if (!isRecording && mediaRecorder == null) return null

        try {
            mediaRecorder?.stop()
        } catch (e: Exception) {
            Log.w(tag, "Error stopping MediaRecorder", e)
        }

        try {
            mediaRecorder?.release()
        } catch (e: Exception) {
            Log.w(tag, "Error releasing MediaRecorder", e)
        }
        mediaRecorder = null
        isRecording = false

        val bytes = outputFile?.let { file ->
            if (file.exists() && file.length() > 0) {
                file.readBytes().also { file.delete() }
            } else {
                null
            }
        }
        outputFile = null
        return bytes
    }

    fun getMaxAmplitude(): Int {
        return if (isRecording) {
            try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (e: Exception) {
                0
            }
        } else {
            0
        }
    }
}