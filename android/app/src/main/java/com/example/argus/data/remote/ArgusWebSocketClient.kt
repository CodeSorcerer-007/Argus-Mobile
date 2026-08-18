package com.example.argus.data.remote

import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import okhttp3.*
import java.util.concurrent.TimeUnit

@Serializable
data class WireEncryptedPayload(
    val id: String,
    val conversationId: String,
    val senderId: String,
    val recipientId: String,
    val dhPublicKeyBase64: String,
    val sequenceNumber: Int,
    val previousChainLength: Int,
    val ivBase64: String,
    val ciphertextBase64: String,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val mediaSize: Long? = null,
    val replyToMessageId: String? = null,
    val timestamp: Long,
    val expiresAt: Long? = null,
    val status: String = "SENT"
)

sealed interface WebSocketInboundEvent {
    data class AuthSuccess(val userId: String) : WebSocketInboundEvent
    data class NewMessage(val payload: WireEncryptedPayload) : WebSocketInboundEvent
    data class MessageStatusUpdate(val messageId: String, val status: String) : WebSocketInboundEvent
    data class Typing(val userId: String, val conversationId: String, val isTyping: Boolean) : WebSocketInboundEvent
    data class Presence(val userId: String, val isOnline: Boolean, val lastSeen: Long) : WebSocketInboundEvent
    data class IncomingCall(val callerId: String, val callId: String, val callType: String, val sdp: String?) : WebSocketInboundEvent
    data class CallAnswered(val callId: String, val sdp: String?) : WebSocketInboundEvent
    data class RemoteIceCandidate(val callId: String, val candidate: String?) : WebSocketInboundEvent
    data class CallTerminated(val callId: String, val reason: String?) : WebSocketInboundEvent
}

class ArgusWebSocketClient(
    private val getWsUrl: () -> String = { "ws://10.0.2.2:8080/ws" },
    private val getAuthToken: () -> String?,
    private val getDeviceId: () -> String?
) {
    private val TAG = "ArgusWS"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val json = Json { ignoreUnknownKeys = true }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _inboundEvents = MutableSharedFlow<WebSocketInboundEvent>(extraBufferCapacity = 64)
    val inboundEvents: SharedFlow<WebSocketInboundEvent> = _inboundEvents.asSharedFlow()

    private var shouldReconnect = true

    fun connect() {
        val token = getAuthToken() ?: return
        shouldReconnect = true

        val currentWsUrl = getWsUrl()
        val request = Request.Builder().url(currentWsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(ws: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, sending AUTH")
                _connectionState.value = true
                val authPayload = """{"type":"AUTH","token":"$token","deviceId":"${getDeviceId() ?: "android_1"}"}"""
                ws.send(authPayload)
            }

            override fun onMessage(ws: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
            }

            override fun onClosed(ws: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
                scheduleReconnect()
            }

            override fun onFailure(ws: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket connection failed: ${t.message}")
                _connectionState.value = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        scope.launch {
            delay(3000)
            if (getAuthToken() != null) {
                connect()
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        webSocket?.close(1000, "Client closed")
        webSocket = null
        _connectionState.value = false
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val root = json.decodeFromString<JsonObject>(text)
            val type = root["type"]?.toString()?.replace("\"", "") ?: return

            when (type) {
                "AUTH_SUCCESS" -> {
                    val userId = root["userId"]?.toString()?.replace("\"", "") ?: ""
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.AuthSuccess(userId)) }
                }
                "NEW_MESSAGE" -> {
                    val payloadJson = root["payload"]?.toString() ?: return
                    val payload = json.decodeFromString<WireEncryptedPayload>(payloadJson)
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.NewMessage(payload)) }
                }
                "MESSAGE_STATUS" -> {
                    val msgId = root["messageId"]?.toString()?.replace("\"", "") ?: return
                    val status = root["status"]?.toString()?.replace("\"", "") ?: "SENT"
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.MessageStatusUpdate(msgId, status)) }
                }
                "TYPING" -> {
                    val userId = root["userId"]?.toString()?.replace("\"", "") ?: return
                    val convId = root["conversationId"]?.toString()?.replace("\"", "") ?: return
                    val isTyping = root["isTyping"]?.toString()?.toBoolean() ?: false
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.Typing(userId, convId, isTyping)) }
                }
                "PRESENCE" -> {
                    val userId = root["userId"]?.toString()?.replace("\"", "") ?: return
                    val isOnline = root["isOnline"]?.toString()?.toBoolean() ?: false
                    val lastSeen = root["lastSeen"]?.toString()?.toLongOrNull() ?: System.currentTimeMillis()
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.Presence(userId, isOnline, lastSeen)) }
                }
                "INCOMING_CALL" -> {
                    val callerId = root["callerId"]?.toString()?.replace("\"", "") ?: return
                    val callId = root["callId"]?.toString()?.replace("\"", "") ?: return
                    val callType = root["callType"]?.toString()?.replace("\"", "") ?: "VOICE"
                    val sdp = root["sdp"]?.toString()
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.IncomingCall(callerId, callId, callType, sdp)) }
                }
                "CALL_ANSWERED" -> {
                    val callId = root["callId"]?.toString()?.replace("\"", "") ?: return
                    val sdp = root["sdp"]?.toString()
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.CallAnswered(callId, sdp)) }
                }
                "REMOTE_ICE_CANDIDATE" -> {
                    val callId = root["callId"]?.toString()?.replace("\"", "") ?: return
                    val candidate = root["candidate"]?.toString()
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.RemoteIceCandidate(callId, candidate)) }
                }
                "CALL_TERMINATED" -> {
                    val callId = root["callId"]?.toString()?.replace("\"", "") ?: return
                    val reason = root["reason"]?.toString()?.replace("\"", "")
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.CallTerminated(callId, reason)) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling inbound WebSocket message", e)
        }
    }

    fun sendEncryptedMessage(payload: WireEncryptedPayload): Boolean {
        val jsonPayload = json.encodeToString(payload)
        val wireMsg = """{"type":"SEND_MESSAGE","payload":$jsonPayload}"""
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendDeliveryAck(messageId: String, senderId: String): Boolean {
        val msg = """{"type":"ACK_DELIVERED","messageId":"$messageId","senderId":"$senderId"}"""
        return webSocket?.send(msg) ?: false
    }

    fun sendReadAck(messageId: String, senderId: String): Boolean {
        val msg = """{"type":"ACK_READ","messageId":"$messageId","senderId":"$senderId"}"""
        return webSocket?.send(msg) ?: false
    }

    fun sendTyping(recipientId: String, conversationId: String, isTyping: Boolean): Boolean {
        val type = if (isTyping) "TYPING_START" else "TYPING_STOP"
        val msg = """{"type":"$type","recipientId":"$recipientId","conversationId":"$conversationId"}"""
        return webSocket?.send(msg) ?: false
    }

    fun sendCallOffer(targetUserId: String, callId: String, callType: String, sdp: String): Boolean {
        val msg = """{"type":"CALL_OFFER","targetUserId":"$targetUserId","callId":"$callId","callType":"$callType","sdp":"$sdp"}"""
        return webSocket?.send(msg) ?: false
    }

    fun sendCallAnswer(targetUserId: String, callId: String, sdp: String): Boolean {
        val msg = """{"type":"CALL_ANSWER","targetUserId":"$targetUserId","callId":"$callId","sdp":"$sdp"}"""
        return webSocket?.send(msg) ?: false
    }

    fun sendIceCandidate(targetUserId: String, callId: String, candidate: String): Boolean {
        val msg = """{"type":"ICE_CANDIDATE","targetUserId":"$targetUserId","callId":"$callId","candidate":"$candidate"}"""
        return webSocket?.send(msg) ?: false
    }

    fun sendCallEnd(targetUserId: String, callId: String): Boolean {
        val msg = """{"type":"CALL_END","targetUserId":"$targetUserId","callId":"$callId"}"""
        return webSocket?.send(msg) ?: false
    }
}
