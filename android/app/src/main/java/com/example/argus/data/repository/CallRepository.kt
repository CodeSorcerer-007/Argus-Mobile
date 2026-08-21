package com.example.argus.data.repository

import com.example.argus.data.local.ArgusLocalStore
import com.example.argus.data.model.CallRecord
import com.example.argus.data.model.CallStatus
import com.example.argus.data.model.CallType
import com.example.argus.data.remote.ArgusWebSocketClient
import com.example.argus.data.remote.WebSocketInboundEvent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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
    val encryptionVerified: Boolean = true
)

class CallRepository(
    private val localStore: ArgusLocalStore,
    private val webSocketClient: ArgusWebSocketClient
) {
    private val scope = CoroutineScope(Dispatchers.IO)
    val callHistory: StateFlow<List<CallRecord>> = localStore.callsFlow

    private val _activeCallFlow = MutableStateFlow<ActiveCallState?>(null)
    val activeCallFlow: StateFlow<ActiveCallState?> = _activeCallFlow.asStateFlow()

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
                                callType = callTypeEnum
                            )
                        }
                    }
                    is WebSocketInboundEvent.CallAnswered -> {
                        val current = _activeCallFlow.value
                        if (current != null && current.callId == event.callId) {
                            _activeCallFlow.value = current.copy(status = CallStatus.CONNECTED)
                        }
                    }
                    is WebSocketInboundEvent.CallTerminated -> {
                        val current = _activeCallFlow.value
                        if (current != null && current.callId == event.callId) {
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

    fun initiateCall(peerId: String, peerName: String, peerAvatar: String?, callType: CallType): ActiveCallState {
        val callId = UUID.randomUUID().toString()
        val state = ActiveCallState(
            callId = callId,
            peerId = peerId,
            peerName = peerName,
            peerAvatar = peerAvatar,
            callType = callType,
            status = CallStatus.OUTGOING,
            isMuted = false,
            isSpeakerOn = callType == CallType.VIDEO,
            isVideoEnabled = callType == CallType.VIDEO
        )
        _activeCallFlow.value = state

        webSocketClient.sendCallOffer(
            targetUserId = peerId,
            callId = callId,
            callType = callType.name,
            sdp = "v=0\r\no=Argus 123 456 IN IP4 0.0.0.0\r\ns=Argus E2EE WebRTC\r\n"
        )

        return state
    }

    fun receiveIncomingCall(callerId: String, callerName: String, callerAvatar: String?, callId: String, callType: CallType) {
        val state = ActiveCallState(
            callId = callId,
            peerId = callerId,
            peerName = callerName,
            peerAvatar = callerAvatar,
            callType = callType,
            status = CallStatus.INCOMING
        )
        _activeCallFlow.value = state
    }

    fun acceptCall() {
        val current = _activeCallFlow.value ?: return
        _activeCallFlow.value = current.copy(status = CallStatus.CONNECTED)

        webSocketClient.sendCallAnswer(
            targetUserId = current.peerId,
            callId = current.callId,
            sdp = "v=0\r\no=Argus 456 789 IN IP4 0.0.0.0\r\ns=Argus E2EE WebRTC Answer\r\n"
        )
    }

    fun rejectCall() {
        val current = _activeCallFlow.value ?: return
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
        _activeCallFlow.value = current.copy(isMuted = !current.isMuted)
    }

    fun toggleSpeaker() {
        val current = _activeCallFlow.value ?: return
        _activeCallFlow.value = current.copy(isSpeakerOn = !current.isSpeakerOn)
    }

    fun toggleVideo() {
        val current = _activeCallFlow.value ?: return
        _activeCallFlow.value = current.copy(isVideoEnabled = !current.isVideoEnabled)
    }

    fun toggleCamera() {
        val current = _activeCallFlow.value ?: return
        _activeCallFlow.value = current.copy(isFrontCamera = !current.isFrontCamera)
    }

    fun endCall(durationSec: Int = 0) {
        val current = _activeCallFlow.value ?: return

        webSocketClient.sendCallEnd(current.peerId, current.callId)

        val record = CallRecord(
            id = current.callId,
            peerId = current.peerId,
            peerName = current.peerName,
            peerAvatar = current.peerAvatar,
            callType = current.callType,
            status = if (durationSec > 0) CallStatus.ENDED else CallStatus.MISSED,
            durationSec = durationSec,
            timestamp = System.currentTimeMillis()
        )
        localStore.saveCall(record)

        _activeCallFlow.value = null
    }
}
