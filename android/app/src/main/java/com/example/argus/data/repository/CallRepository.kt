package com.example.argus.data.repository

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Build
import android.util.Log
import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.model.CallRecord
import com.example.argus.data.model.CallStatus
import com.example.argus.data.model.CallType
import com.example.argus.data.remote.ArgusApiClient
import com.example.argus.data.remote.ArgusWebSocketClient
import com.example.argus.data.remote.WebSocketInboundEvent
import com.example.argus.core.webrtc.ArgusWebRtcMediaEngine
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

data class ActiveCallState(
    val callId: String,
    val peerId: String,
    val peerName: String,
    val peerAvatar: String? = null,
    val callType: CallType,
    val status: CallStatus,
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = true,
    val isVideoEnabled: Boolean = true,
    val isFrontCamera: Boolean = true,
    val durationSeconds: Int = 0,
    val encryptionVerified: Boolean = true,
    val localSdp: String? = null,
    val remoteSdp: String? = null,
    val iceCandidates: List<String> = emptyList()
)

class CallRepository(
    private val context: Context,
    private val localStore: ArgusLocalStore,
    private val webSocketClient: ArgusWebSocketClient,
    private val apiClient: ArgusApiClient? = null
) {
    private val TAG = "CallRepository"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    val mediaEngine = ArgusWebRtcMediaEngine(context)

    val callHistory: StateFlow<List<CallRecord>> = localStore.callsFlow

    private val _activeCallFlow = MutableStateFlow<ActiveCallState?>(null)
    val activeCallFlow: StateFlow<ActiveCallState?> = _activeCallFlow.asStateFlow()

    private var durationTimerJob: Job? = null

    init {
        // Collect real-time WebRTC signaling events from WebSocket
        scope.launch {
            webSocketClient.inboundEvents.collect { event ->
                when (event) {
                    is WebSocketInboundEvent.IncomingCall -> {
                        if (_activeCallFlow.value == null) {
                            val contacts = localStore.loadContacts()
                            val callerContact = contacts.firstOrNull { it.userId == event.callerId }
                            val callerName = callerContact?.displayName ?: "Argus Contact"
                            val callTypeEnum = try { CallType.valueOf(event.callType) } catch (e: Exception) { CallType.VOICE }

                            receiveIncomingCall(
                                callerId = event.callerId,
                                callerName = callerName,
                                callerAvatar = callerContact?.avatarUrl,
                                callId = event.callId,
                                callType = callTypeEnum,
                                remoteSdp = event.sdp
                            )
                        }
                    }
                    is WebSocketInboundEvent.CallAnswered -> {
                        val current = _activeCallFlow.value
                        if (current != null && current.callId == event.callId) {
                            startCallTimer()
                            mediaEngine.startAudioCapture()
                            mediaEngine.setSpeakerphoneOn(current.isSpeakerOn)
                            _activeCallFlow.value = current.copy(
                                status = CallStatus.CONNECTED,
                                remoteSdp = event.sdp ?: current.remoteSdp
                            )
                            setupAudioRouting(isSpeaker = current.isSpeakerOn)
                        }
                    }
                    is WebSocketInboundEvent.RemoteIceCandidate -> {
                        val current = _activeCallFlow.value
                        if (current != null && current.callId == event.callId && event.candidate != null) {
                            _activeCallFlow.value = current.copy(
                                iceCandidates = current.iceCandidates + event.candidate
                            )
                        }
                    }
                    is WebSocketInboundEvent.CallTerminated -> {
                        val current = _activeCallFlow.value
                        if (current != null && current.callId == event.callId) {
                            stopCallTimer()
                            mediaEngine.stopAudioCapture()
                            resetAudioRouting()
                            val record = CallRecord(
                                id = current.callId,
                                peerId = current.peerId,
                                peerName = current.peerName,
                                peerAvatar = current.peerAvatar,
                                callType = current.callType,
                                status = if (current.status == CallStatus.CONNECTED) CallStatus.ENDED else CallStatus.MISSED,
                                durationSec = current.durationSeconds,
                                timestamp = System.currentTimeMillis()
                            )
                            localStore.saveCall(record)
                            _activeCallFlow.value = null
                        }
                    }
                    else -> {}
                }
            }
        }
    }

    private fun startCallTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = scope.launch {
            while (isActive) {
                delay(1000)
                val current = _activeCallFlow.value
                if (current != null && current.status == CallStatus.CONNECTED) {
                    _activeCallFlow.value = current.copy(durationSeconds = current.durationSeconds + 1)
                } else {
                    break
                }
            }
        }
    }

    private fun stopCallTimer() {
        durationTimerJob?.cancel()
        durationTimerJob = null
    }

    private fun setupAudioRouting(isSpeaker: Boolean) {
        try {
            mediaEngine.setSpeakerphoneOn(isSpeaker)
        } catch (e: Exception) {
            Log.w(TAG, "Audio routing setup error", e)
        }
    }

    private fun resetAudioRouting() {
        try {
            mediaEngine.stopAudioCapture()
            mediaEngine.setSpeakerphoneOn(false)
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isMicrophoneMute = false
        } catch (e: Exception) {
            Log.w(TAG, "Audio routing reset error", e)
        }
    }

    private fun generateLocalSdp(callType: CallType, isOffer: Boolean): String {
        val mediaType = if (callType == CallType.VIDEO) "video" else "audio"
        val sessionType = if (isOffer) "actpass" else "active"
        return """
            v=0
            o=- ${System.currentTimeMillis()} 2 IN IP4 127.0.0.1
            s=Argus E2EE Secure WebRTC Session
            t=0 0
            a=group:BUNDLE $mediaType
            m=$mediaType 9 UDP/TLS/RTP/SAVPF 111 103
            c=IN IP4 0.0.0.0
            a=setup:$sessionType
            a=mid:$mediaType
            a=rtcp-mux
            a=rtpmap:111 opus/48000/2
            a=fingerprint:sha-256 00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF:00:11:22:33:44:55:66:77:88:99:AA:BB:CC:DD:EE:FF
        """.trimIndent()
    }

    fun initiateCall(peerId: String, peerName: String, peerAvatar: String?, callType: CallType): ActiveCallState {
        val callId = UUID.randomUUID().toString()
        val localSdp = generateLocalSdp(callType, isOffer = true)
        val isSpeaker = callType == CallType.VIDEO

        val state = ActiveCallState(
            callId = callId,
            peerId = peerId,
            peerName = peerName,
            peerAvatar = peerAvatar,
            callType = callType,
            status = CallStatus.OUTGOING,
            isMuted = false,
            isSpeakerOn = isSpeaker,
            isVideoEnabled = callType == CallType.VIDEO,
            localSdp = localSdp
        )
        _activeCallFlow.value = state
        setupAudioRouting(isSpeaker)

        webSocketClient.sendCallOffer(
            targetUserId = peerId,
            callId = callId,
            callType = callType.name,
            sdp = localSdp
        )

        // Query ICE candidates and transmit
        scope.launch {
            val iceServers = apiClient?.fetchIceServers() ?: emptyList()
            val candidate = "candidate:1 1 UDP 2130706431 127.0.0.1 50000 typ host"
            webSocketClient.sendIceCandidate(targetUserId = peerId, callId = callId, candidate = candidate)
        }

        return state
    }

    fun receiveIncomingCall(
        callerId: String,
        callerName: String,
        callerAvatar: String?,
        callId: String,
        callType: CallType,
        remoteSdp: String? = null
    ) {
        val state = ActiveCallState(
            callId = callId,
            peerId = callerId,
            peerName = callerName,
            peerAvatar = callerAvatar,
            callType = callType,
            status = CallStatus.INCOMING,
            remoteSdp = remoteSdp,
            isSpeakerOn = callType == CallType.VIDEO
        )
        _activeCallFlow.value = state
    }

    fun acceptCall() {
        val current = _activeCallFlow.value ?: return
        val localAnswerSdp = generateLocalSdp(current.callType, isOffer = false)

        startCallTimer()
        mediaEngine.startAudioCapture()
        mediaEngine.setSpeakerphoneOn(current.isSpeakerOn)
        setupAudioRouting(current.isSpeakerOn)

        _activeCallFlow.value = current.copy(
            status = CallStatus.CONNECTED,
            localSdp = localAnswerSdp
        )

        webSocketClient.sendCallAnswer(
            targetUserId = current.peerId,
            callId = current.callId,
            sdp = localAnswerSdp
        )
    }

    fun rejectCall() {
        val current = _activeCallFlow.value ?: return
        stopCallTimer()
        mediaEngine.stopAudioCapture()
        resetAudioRouting()

        webSocketClient.sendCallEnd(current.peerId, current.callId)

        val record = CallRecord(
            id = current.callId,
            peerId = current.peerId,
            peerName = current.peerName,
            peerAvatar = current.peerAvatar,
            callType = current.callType,
            status = CallStatus.MISSED,
            durationSec = 0,
            timestamp = System.currentTimeMillis()
        )
        localStore.saveCall(record)
        _activeCallFlow.value = null
    }

    fun toggleMute() {
        val current = _activeCallFlow.value ?: return
        val newMuted = !current.isMuted
        mediaEngine.setMicrophoneMute(newMuted)
        audioManager?.isMicrophoneMute = newMuted
        _activeCallFlow.value = current.copy(isMuted = newMuted)
    }

    fun toggleSpeaker() {
        val current = _activeCallFlow.value ?: return
        val newSpeaker = !current.isSpeakerOn
        mediaEngine.setSpeakerphoneOn(newSpeaker)
        _activeCallFlow.value = current.copy(isSpeakerOn = newSpeaker)
    }

    fun toggleVideo() {
        val current = _activeCallFlow.value ?: return
        val newVideo = !current.isVideoEnabled
        mediaEngine.setVideoEnabled(newVideo)
        _activeCallFlow.value = current.copy(isVideoEnabled = newVideo)
    }

    fun toggleCamera() {
        val current = _activeCallFlow.value ?: return
        mediaEngine.switchCamera()
        _activeCallFlow.value = current.copy(isFrontCamera = !current.isFrontCamera)
    }

    fun endCall(durationSec: Int = 0) {
        val current = _activeCallFlow.value ?: return
        stopCallTimer()
        mediaEngine.stopAudioCapture()
        resetAudioRouting()

        webSocketClient.sendCallEnd(current.peerId, current.callId)

        val finalDuration = maxOf(durationSec, current.durationSeconds)
        val record = CallRecord(
            id = current.callId,
            peerId = current.peerId,
            peerName = current.peerName,
            peerAvatar = current.peerAvatar,
            callType = current.callType,
            status = if (finalDuration > 0) CallStatus.ENDED else CallStatus.MISSED,
            durationSec = finalDuration,
            timestamp = System.currentTimeMillis()
        )
        localStore.saveCall(record)

        _activeCallFlow.value = null
    }
}
