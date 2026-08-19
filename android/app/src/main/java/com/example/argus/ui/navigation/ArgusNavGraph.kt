package com.example.argus.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.example.argus.AppContainer
import com.example.argus.data.model.CallType
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.User
import com.example.argus.theme.ObsidianBlack
import com.example.argus.ui.ai.AiAssistantScreen
import com.example.argus.ui.auth.OtpVerifyScreen
import com.example.argus.ui.auth.PhoneAuthScreen
import com.example.argus.ui.auth.WelcomeScreen
import com.example.argus.ui.call.CallScreen
import com.example.argus.ui.chat.ChatScreen
import com.example.argus.ui.main.MainScreen
import com.example.argus.ui.security.SecurityVerificationScreen
import com.example.argus.ui.settings.SettingsScreen
import com.example.argus.ui.vault.ArgusVaultScreen
import com.example.argus.ui.shield.ArgusShieldScreen
import kotlinx.coroutines.launch

sealed class Screen {
    object Welcome : Screen()
    object PhoneAuth : Screen()
    data class OtpVerify(val phoneNumber: String, val suggestedCode: String = "") : Screen()
    object Main : Screen()
    data class Chat(val conversationId: String) : Screen()
    data class SecurityVerify(
        val peerName: String,
        val peerUserId: String,
        val peerIdentityKey: String,
        val isVerified: Boolean
    ) : Screen()
    object Call : Screen()
    object Vault : Screen()
    object Shield : Screen()
    object AiAssistant : Screen()
    object Settings : Screen()
}

@Composable
fun ArgusNavGraph(container: AppContainer) {
    val isLoggedIn = container.authRepository.isLoggedIn()
    var currentScreen by remember {
        mutableStateOf<Screen>(if (isLoggedIn) Screen.Main else Screen.Welcome)
    }

    val coroutineScope = rememberCoroutineScope()

    val conversations by container.messageRepository.conversations.collectAsState()
    val messagesMap by container.messageRepository.messages.collectAsState()
    val contacts by container.localStore.contactsFlow.collectAsState()
    val calls by container.callRepository.callHistory.collectAsState()
    val activeCallState by container.callRepository.activeCallFlow.collectAsState()

    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(ObsidianBlack)
    ) {
        when (val screen = currentScreen) {
            is Screen.Welcome -> {
                WelcomeScreen(
                    onGetStartedClick = { currentScreen = Screen.PhoneAuth }
                )
            }

            is Screen.PhoneAuth -> {
                BackHandler {
                    currentScreen = Screen.Welcome
                }
                PhoneAuthScreen(
                    currentServerUrl = container.preferences.getServerUrl(),
                    onSaveServerUrl = { newUrl ->
                        container.preferences.setServerUrl(newUrl)
                        authErrorMessage = null
                    },
                    onRequestOtp = { phone ->
                        isAuthLoading = true
                        authErrorMessage = null
                        coroutineScope.launch {
                            try {
                                val resp = container.authRepository.requestOtp(phone)
                                isAuthLoading = false
                                if (resp.success) {
                                    authErrorMessage = null
                                    val code = resp.code ?: resp.devCode ?: if (phone.endsWith("0000")) "000000" else ""
                                    currentScreen = Screen.OtpVerify(phoneNumber = phone, suggestedCode = code)
                                } else {
                                    authErrorMessage = resp.error ?: "Cannot reach server. Please check your internet connection."
                                }
                            } catch (e: Exception) {
                                isAuthLoading = false
                                authErrorMessage = "Connection failed: ${e.localizedMessage ?: "Unknown network error"}"
                            }
                        }
                    },
                    isLoading = isAuthLoading,
                    errorMessage = authErrorMessage
                )
            }

            is Screen.OtpVerify -> {
                BackHandler {
                    authErrorMessage = null
                    currentScreen = Screen.PhoneAuth
                }
                OtpVerifyScreen(
                    phoneNumber = screen.phoneNumber,
                    initialCode = screen.suggestedCode,
                    onVerifyOtp = { code ->
                        isAuthLoading = true
                        authErrorMessage = null
                        coroutineScope.launch {
                            try {
                                val result = container.authRepository.verifyOtp(screen.phoneNumber, code)
                                isAuthLoading = false
                                if (result.isSuccess) {
                                    authErrorMessage = null
                                    currentScreen = Screen.Main
                                } else {
                                    authErrorMessage = result.exceptionOrNull()?.localizedMessage ?: "Verification failed. Check the code."
                                }
                            } catch (e: Exception) {
                                isAuthLoading = false
                                authErrorMessage = "Authentication error: ${e.localizedMessage}"
                            }
                        }
                    },
                    onResendClick = {
                        coroutineScope.launch {
                            try {
                                val resp = container.authRepository.requestOtp(screen.phoneNumber)
                                if (!resp.success) {
                                    authErrorMessage = resp.error ?: "Failed to resend code."
                                }
                            } catch (e: Exception) {
                                authErrorMessage = "Resend error: ${e.localizedMessage}"
                            }
                        }
                    },
                    onBackClick = {
                        authErrorMessage = null
                        currentScreen = Screen.PhoneAuth
                    },
                    isLoading = isAuthLoading,
                    errorMessage = authErrorMessage
                )
            }

            is Screen.Main -> {
                MainScreen(
                    conversations = conversations,
                    contacts = contacts,
                    calls = calls,
                    authRepository = container.authRepository,
                    onConversationClick = { convId ->
                        container.localStore.loadMessagesForConversation(convId)
                        currentScreen = Screen.Chat(convId)
                    },
                    onContactClick = { contact ->
                        val convId = "conv_${contact.userId}"
                        val existing = conversations.firstOrNull { it.id == convId }
                        if (existing == null) {
                            val newConv = Conversation(
                                id = convId,
                                title = contact.displayName,
                                participantIds = listOf(contact.userId),
                                avatarUrl = contact.avatarUrl
                            )
                            container.localStore.upsertConversation(newConv)
                        }
                        container.localStore.loadMessagesForConversation(convId)
                        currentScreen = Screen.Chat(convId)
                    },
                    onStartCallClick = { contact, callType ->
                        container.callRepository.initiateCall(
                            peerId = contact.userId,
                            peerName = contact.displayName,
                            peerAvatar = contact.avatarUrl,
                            callType = callType
                        )
                        currentScreen = Screen.Call
                    },
                    onVaultClick = { currentScreen = Screen.Vault },
                    onShieldClick = { currentScreen = Screen.Shield },
                    onSettingsClick = { currentScreen = Screen.Settings },
                    onAiAssistantClick = { currentScreen = Screen.AiAssistant }
                )
            }

            is Screen.Chat -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                val conv = conversations.firstOrNull { it.id == screen.conversationId }
                    ?: Conversation(id = screen.conversationId, title = "Secure Chat", participantIds = emptyList())
                val msgs = messagesMap[screen.conversationId] ?: emptyList()
                val recipientId = conv.participantIds.firstOrNull() ?: "peer_1"

                ChatScreen(
                    conversation = conv,
                    messages = msgs,
                    onBackClick = { currentScreen = Screen.Main },
                    onSendMessage = { text, mediaUri, mediaType, size ->
                        coroutineScope.launch {
                            container.messageRepository.sendMessage(
                                conversationId = conv.id,
                                recipientId = recipientId,
                                text = text,
                                mediaUri = mediaUri,
                                mediaType = mediaType,
                                mediaSizeBytes = size,
                                disappearingDurationSec = conv.disappearingDurationSec
                            )
                        }
                    },
                    onVoiceCallClick = {
                        container.callRepository.initiateCall(
                            peerId = recipientId,
                            peerName = conv.title,
                            peerAvatar = conv.avatarUrl,
                            callType = CallType.VOICE
                        )
                        currentScreen = Screen.Call
                    },
                    onVideoCallClick = {
                        container.callRepository.initiateCall(
                            peerId = recipientId,
                            peerName = conv.title,
                            peerAvatar = conv.avatarUrl,
                            callType = CallType.VIDEO
                        )
                        currentScreen = Screen.Call
                    },
                    onVerifySecurityClick = {
                        val contact = contacts.firstOrNull { it.userId == recipientId }
                        currentScreen = Screen.SecurityVerify(
                            peerName = conv.title,
                            peerUserId = recipientId,
                            peerIdentityKey = contact?.identityKeyBase64 ?: "mock_peer_key",
                            isVerified = contact?.isVerified ?: false
                        )
                    },
                    onReactionClick = { msg, emoji ->
                        container.messageRepository.addReaction(msg, emoji)
                    },
                    aiRepository = container.aiAssistantRepository
                )
            }

            is Screen.SecurityVerify -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                val myIdentity = container.authRepository.getOrCreateIdentityKeyPair()
                val currentUserId = container.preferences.loadCurrentUser()?.id ?: "me"

                SecurityVerificationScreen(
                    peerName = screen.peerName,
                    peerUserId = screen.peerUserId,
                    peerIdentityKeyBase64 = screen.peerIdentityKey,
                    myUserId = currentUserId,
                    myIdentityKeyBase64 = myIdentity.publicKeyBase64,
                    isCurrentlyVerified = screen.isVerified,
                    onMarkVerified = { verified ->
                        val contact = contacts.firstOrNull { it.userId == screen.peerUserId }
                        if (contact != null) {
                            container.localStore.upsertContact(contact.copy(isVerified = verified))
                        }
                    },
                    onBackClick = { currentScreen = Screen.Main }
                )
            }

            is Screen.Call -> {
                val call = activeCallState
                if (call != null) {
                    CallScreen(
                        callState = call,
                        onMuteToggle = { container.callRepository.toggleMute() },
                        onSpeakerToggle = { container.callRepository.toggleSpeaker() },
                        onVideoToggle = { container.callRepository.toggleVideo() },
                        onCameraSwitch = { container.callRepository.toggleCamera() },
                        onEndCall = { dur ->
                            container.callRepository.endCall(dur)
                            currentScreen = Screen.Main
                        }
                    )
                } else {
                    currentScreen = Screen.Main
                }
            }

            is Screen.Vault -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                ArgusVaultScreen(
                    vaultRepository = container.vaultRepository,
                    onBackClick = { currentScreen = Screen.Main }
                )
            }

            is Screen.Shield -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                ArgusShieldScreen(
                    shieldRepository = container.shieldRepository,
                    onBackClick = { currentScreen = Screen.Main }
                )
            }

            is Screen.AiAssistant -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                AiAssistantScreen(
                    aiRepository = container.aiAssistantRepository,
                    onBackClick = { currentScreen = Screen.Main }
                )
            }

            is Screen.Settings -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                SettingsScreen(
                    preferences = container.preferences,
                    authRepository = container.authRepository,
                    onBackClick = { currentScreen = Screen.Main },
                    onLogoutClick = {
                        container.authRepository.logout()
                        currentScreen = Screen.Welcome
                    }
                )
            }
        }
    }
}
