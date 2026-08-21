package com.example.argus.core.webrtc

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaRecorder
import android.media.audiofx.AcousticEchoCanceler
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs
import kotlin.math.sqrt

class ArgusWebRtcMediaEngine(private val context: Context) {
    private val TAG = "ArgusWebRtcEngine"
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

    private var audioRecord: AudioRecord? = null
    private var echoCanceler: AcousticEchoCanceler? = null
    private var noiseSuppressor: NoiseSuppressor? = null
    private var isRecording = false
    private var audioJob: Job? = null

    private val _audioLevelFlow = MutableStateFlow(0f)
    val audioLevelFlow: StateFlow<Float> = _audioLevelFlow.asStateFlow()

    private val _isMicrophoneMuted = MutableStateFlow(false)
    val isMicrophoneMuted: StateFlow<Boolean> = _isMicrophoneMuted.asStateFlow()

    private val _isSpeakerphoneOn = MutableStateFlow(true)
    val isSpeakerphoneOn: StateFlow<Boolean> = _isSpeakerphoneOn.asStateFlow()

    private val _isVideoTrackEnabled = MutableStateFlow(true)
    val isVideoTrackEnabled: StateFlow<Boolean> = _isVideoTrackEnabled.asStateFlow()

    private val _isFrontCamera = MutableStateFlow(true)
    val isFrontCamera: StateFlow<Boolean> = _isFrontCamera.asStateFlow()

    @SuppressLint("MissingPermission")
    fun startAudioCapture(): Boolean {
        if (isRecording) return true

        return try {
            val sampleRate = 44100
            val channelConfig = AudioFormat.CHANNEL_IN_MONO
            val audioFormat = AudioFormat.ENCODING_PCM_16BIT
            val minBufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
            val bufferSize = maxOf(minBufferSize, 4096)

            val record = AudioRecord(
                MediaRecorder.AudioSource.VOICE_COMMUNICATION,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize
            )

            if (record.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord failed to initialize")
                return false
            }

            // Enable Hardware Acoustic Echo Canceler if available
            if (AcousticEchoCanceler.isAvailable()) {
                try {
                    echoCanceler = AcousticEchoCanceler.create(record.audioSessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "AcousticEchoCanceler enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable AcousticEchoCanceler", e)
                }
            }

            // Enable Hardware Noise Suppressor if available
            if (NoiseSuppressor.isAvailable()) {
                try {
                    noiseSuppressor = NoiseSuppressor.create(record.audioSessionId)?.apply {
                        enabled = true
                    }
                    Log.d(TAG, "NoiseSuppressor enabled")
                } catch (e: Exception) {
                    Log.w(TAG, "Failed to enable NoiseSuppressor", e)
                }
            }

            record.startRecording()
            audioRecord = record
            isRecording = true

            // Read live audio samples and calculate RMS for UI audio visualizer
            audioJob = scope.launch {
                val buffer = ShortArray(bufferSize / 2)
                while (isActive && isRecording) {
                    val read = record.read(buffer, 0, buffer.size)
                    if (read > 0 && !_isMicrophoneMuted.value) {
                        var sum = 0.0
                        for (i in 0 until read) {
                            sum += buffer[i] * buffer[i]
                        }
                        val rms = sqrt(sum / read)
                        val normalized = (rms / 32768.0).toFloat().coerceIn(0f, 1f)
                        _audioLevelFlow.value = normalized
                    } else {
                        _audioLevelFlow.value = 0f
                    }
                    delay(50)
                }
            }

            Log.d(TAG, "Hardware audio capture active")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Error starting WebRTC audio capture", e)
            stopAudioCapture()
            false
        }
    }

    fun stopAudioCapture() {
        isRecording = false
        audioJob?.cancel()
        audioJob = null

        try {
            echoCanceler?.release()
        } catch (e: Exception) {
            // Ignore
        }
        echoCanceler = null

        try {
            noiseSuppressor?.release()
        } catch (e: Exception) {
            // Ignore
        }
        noiseSuppressor = null

        try {
            audioRecord?.apply {
                if (state == AudioRecord.STATE_INITIALIZED) {
                    stop()
                }
                release()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing AudioRecord", e)
        }
        audioRecord = null
        _audioLevelFlow.value = 0f
    }

    fun setMicrophoneMute(muted: Boolean) {
        _isMicrophoneMuted.value = muted
        audioManager?.isMicrophoneMute = muted
    }

    fun setSpeakerphoneOn(speakerOn: Boolean) {
        _isSpeakerphoneOn.value = speakerOn
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speakerDevice = audioManager?.availableCommunicationDevices?.firstOrNull {
                        it.type == android.media.AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speakerDevice != null) {
                        audioManager.setCommunicationDevice(speakerDevice)
                    } else {
                        @Suppress("DEPRECATION")
                        audioManager?.isSpeakerphoneOn = true
                    }
                } else {
                    audioManager?.clearCommunicationDevice()
                    @Suppress("DEPRECATION")
                    audioManager?.isSpeakerphoneOn = false
                }
            } else {
                @Suppress("DEPRECATION")
                audioManager?.isSpeakerphoneOn = speakerOn
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle speakerphone", e)
        }
    }

    fun setVideoEnabled(enabled: Boolean) {
        _isVideoTrackEnabled.value = enabled
    }

    fun switchCamera() {
        _isFrontCamera.value = !_isFrontCamera.value
    }
}
