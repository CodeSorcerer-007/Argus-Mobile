package com.example.argus.core.media

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

class ArgusAudioRecorder(private val context: Context) {
    private val TAG = "ArgusAudioRecorder"
    private var mediaRecorder: MediaRecorder? = null
    private var currentOutputFile: File? = null
    private var startTimeMs: Long = 0L
    private var isRecording = false

    fun isRecordingActive(): Boolean = isRecording

    fun startRecording(outputFile: File): Boolean {
        return try {
            cancelRecording() // Clean up any previous state
            currentOutputFile = outputFile
            startTimeMs = System.currentTimeMillis()

            val recorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(context)
            } else {
                @Suppress("DEPRECATION")
                MediaRecorder()
            }

            recorder.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioSamplingRate(44100)
                setAudioEncodingBitRate(96000)
                setOutputFile(outputFile.absolutePath)
                prepare()
                start()
            }

            mediaRecorder = recorder
            isRecording = true
            Log.d(TAG, "Hardware audio recording started: ${outputFile.absolutePath}")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start hardware audio recording", e)
            cancelRecording()
            false
        }
    }

    fun stopRecording(): Long {
        if (!isRecording) return 0L
        val durationMs = System.currentTimeMillis() - startTimeMs
        try {
            mediaRecorder?.apply {
                stop()
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping MediaRecorder", e)
        } finally {
            mediaRecorder = null
            isRecording = false
        }
        return maxOf(durationMs, 0L)
    }

    fun cancelRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
            }
        } catch (e: Exception) {
            // Ignore
        } finally {
            mediaRecorder = null
            isRecording = false
            currentOutputFile?.let {
                if (it.exists()) {
                    it.delete()
                }
            }
            currentOutputFile = null
        }
    }

    fun getMaxAmplitude(): Int {
        return try {
            if (isRecording) mediaRecorder?.maxAmplitude ?: 0 else 0
        } catch (e: Exception) {
            0
        }
    }
}
