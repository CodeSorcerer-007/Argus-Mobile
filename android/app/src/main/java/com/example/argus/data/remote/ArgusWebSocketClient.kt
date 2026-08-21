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
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
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
    val senderIdentityPublicKeyBase64: String? = null,
    val ephemeralPublicKeyBase64: String? = null,
    val mediaUrl: String? = null,
    val mediaType: String? = null,
    val mediaSize: Long? = null,
    val replyToMessageId: String? = null,
    val timestamp: Long,
    val expiresAt: Long? = null,
    val status: String = "SENT"
)

@Serializable
private data class SendMessageEvent(val type: String = "SEND_MESSAGE", val payload: WireEncryptedPayload)

@Serializable
private data class DeliveryAckEvent(val type: String = "ACK_DELIVERED", val messageId: String, val senderId: String)

@Serializable
private data class ReadAckEvent(val type: String = "ACK_READ", val messageId: String, val senderId: String)

@Serializable
private data class TypingEvent(val type: String, val recipientId: String, val conversationId: String)

@Serializable
private data class CallOfferEvent(val type: String = "CALL_OFFER", val targetUserId: String, val callId: String, val callType: String, val sdp: String)

@Serializable
private data class CallAnswerEvent(val type: String = "CALL_ANSWER", val targetUserId: String, val callId: String, val sdp: String)

@Serializable
private data class IceCandidateEvent(val type: String = "ICE_CANDIDATE", val targetUserId: String, val callId: String, val candidate: String)

@Serializable
private data class CallEndEvent(val type: String = "CALL_END", val targetUserId: String, val callId: String)

@Serializable
private data class AuthEvent(val type: String = "AUTH", val token: String, val deviceId: String)

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
    private val getWsUrl: () -> String = { "wss://argus-backend-5cg3.onrender.com/ws" },
    private val getAuthToken: () -> String?,
    private val getDeviceId: () -> String?
) {
    private val TAG = "ArgusWS"
    private val client = OkHttpClient.Builder()
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(25, TimeUnit.SECONDS)
        .build()

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var webSocket: WebSocket? = null

    private val _connectionState = MutableStateFlow(false)
    val connectionState: StateFlow<Boolean> = _connectionState.asStateFlow()

    private val _inboundEvents = MutableSharedFlow<WebSocketInboundEvent>(extraBufferCapacity = 64)
    val inboundEvents: SharedFlow<WebSocketInboundEvent> = _inboundEvents.asSharedFlow()

    private var shouldReconnect = true
    private var reconnectAttempt = 0
    private var reconnectJob: kotlinx.coroutines.Job? = null

    fun connect() {
        val token = getAuthToken() ?: return
        shouldReconnect = true

        val currentWsUrl = getWsUrl()
        val request = Request.Builder().url(currentWsUrl).build()
        webSocket = client.newWebSocket(request, object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                Log.d(TAG, "WebSocket connected, sending AUTH")
                _connectionState.value = true
                reconnectAttempt = 0
                reconnectJob?.cancel()
                val authPayload = json.encodeToString(
                    AuthEvent(token = token, deviceId = getDeviceId() ?: "android_1")
                )
                webSocket.send(authPayload)
            }

            override fun onMessage(webSocket: WebSocket, text: String) {
                handleIncomingMessage(text)
            }

            override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                _connectionState.value = false
                scheduleReconnect()
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                Log.w(TAG, "WebSocket connection failed: ${t.message}")
                _connectionState.value = false
                scheduleReconnect()
            }
        })
    }

    private fun scheduleReconnect() {
        if (!shouldReconnect) return
        reconnectJob?.cancel()
        reconnectJob = scope.launch {
            val backoffMs = (1000L * (1 shl minOf(reconnectAttempt, 5))) + (0..1000).random()
            reconnectAttempt++
            Log.d(TAG, "Reconnecting WebSocket in ${backoffMs}ms (attempt $reconnectAttempt)")
            delay(backoffMs)
            if (shouldReconnect && getAuthToken() != null) {
                connect()
            }
        }
    }

    fun disconnect() {
        shouldReconnect = false
        reconnectJob?.cancel()
        webSocket?.close(1000, "Client closed")
        webSocket = null
        _connectionState.value = false
    }

    private fun handleIncomingMessage(text: String) {
        try {
            val root = json.decodeFromString<JsonObject>(text)
            val type = root["type"]?.jsonPrimitive?.contentOrNull ?: return

            when (type) {
                "AUTH_SUCCESS" -> {
                    val userId = root["userId"]?.jsonPrimitive?.contentOrNull ?: ""
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.AuthSuccess(userId)) }
                }
                "NEW_MESSAGE" -> {
                    val payloadObj = root["payload"] ?: return
                    val payload = json.decodeFromString<WireEncryptedPayload>(payloadObj.toString())
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.NewMessage(payload)) }
                }
                "MESSAGE_STATUS" -> {
                    val msgId = root["messageId"]?.jsonPrimitive?.contentOrNull ?: return
                    val status = root["status"]?.jsonPrimitive?.contentOrNull ?: "SENT"
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.MessageStatusUpdate(msgId, status)) }
                }
                "TYPING" -> {
                    val userId = root["userId"]?.jsonPrimitive?.contentOrNull ?: return
                    val convId = root["conversationId"]?.jsonPrimitive?.contentOrNull ?: return
                    val isTyping = root["isTyping"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.Typing(userId, convId, isTyping)) }
                }
                "PRESENCE" -> {
                    val userId = root["userId"]?.jsonPrimitive?.contentOrNull ?: return
                    val isOnline = root["isOnline"]?.jsonPrimitive?.contentOrNull?.toBoolean() ?: false
                    val lastSeen = root["lastSeen"]?.jsonPrimitive?.contentOrNull?.toLongOrNull() ?: System.currentTimeMillis()
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.Presence(userId, isOnline, lastSeen)) }
                }
                "INCOMING_CALL" -> {
                    val callerId = root["callerId"]?.jsonPrimitive?.contentOrNull ?: return
                    val callId = root["callId"]?.jsonPrimitive?.contentOrNull ?: return
                    val callType = root["callType"]?.jsonPrimitive?.contentOrNull ?: "VOICE"
                    val sdp = root["sdp"]?.jsonPrimitive?.contentOrNull
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.IncomingCall(callerId, callId, callType, sdp)) }
                }
                "CALL_ANSWERED" -> {
                    val callId = root["callId"]?.jsonPrimitive?.contentOrNull ?: return
                    val sdp = root["sdp"]?.jsonPrimitive?.contentOrNull
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.CallAnswered(callId, sdp)) }
                }
                "REMOTE_ICE_CANDIDATE" -> {
                    val callId = root["callId"]?.jsonPrimitive?.contentOrNull ?: return
                    val candidate = root["candidate"]?.jsonPrimitive?.contentOrNull
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.RemoteIceCandidate(callId, candidate)) }
                }
                "CALL_TERMINATED" -> {
                    val callId = root["callId"]?.jsonPrimitive?.contentOrNull ?: return
                    val reason = root["reason"]?.jsonPrimitive?.contentOrNull
                    scope.launch { _inboundEvents.emit(WebSocketInboundEvent.CallTerminated(callId, reason)) }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error handling inbound WebSocket message", e)
        }
    }

    fun sendEncryptedMessage(payload: WireEncryptedPayload): Boolean {
        val wireMsg = json.encodeToString(SendMessageEvent(payload = payload))
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendDeliveryAck(messageId: String, senderId: String): Boolean {
        val wireMsg = json.encodeToString(DeliveryAckEvent(messageId = messageId, senderId = senderId))
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendReadAck(messageId: String, senderId: String): Boolean {
        val wireMsg = json.encodeToString(ReadAckEvent(messageId = messageId, senderId = senderId))
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendTyping(recipientId: String, conversationId: String, isTyping: Boolean): Boolean {
        val type = if (isTyping) "TYPING_START" else "TYPING_STOP"
        val wireMsg = json.encodeToString(TypingEvent(type = type, recipientId = recipientId, conversationId = conversationId))
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendCallOffer(targetUserId: String, callId: String, callType: String, sdp: String): Boolean {
        val wireMsg = json.encodeToString(CallOfferEvent(targetUserId = targetUserId, callId = callId, callType = callType, sdp = sdp))
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendCallAnswer(targetUserId: String, callId: String, sdp: String): Boolean {
        val wireMsg = json.encodeToString(CallAnswerEvent(targetUserId = targetUserId, callId = callId, sdp = sdp))
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendIceCandidate(targetUserId: String, callId: String, candidate: String): Boolean {
        val wireMsg = json.encodeToString(IceCandidateEvent(targetUserId = targetUserId, callId = callId, candidate = candidate))
        return webSocket?.send(wireMsg) ?: false
    }

    fun sendCallEnd(targetUserId: String, callId: String): Boolean {
        val wireMsg = json.encodeToString(CallEndEvent(targetUserId = targetUserId, callId = callId))
        return webSocket?.send(wireMsg) ?: false
    }
}
