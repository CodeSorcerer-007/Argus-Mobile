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
import com.example.argus.data.model.Contact
import com.example.argus.data.model.Conversation
import com.example.argus.data.model.User
import com.example.argus.theme.ObsidianBlack
import com.example.argus.ui.ai.AiAssistantScreen
import com.example.argus.ui.auth.ArgusAuthScreen
import com.example.argus.ui.auth.WelcomeScreen
import com.example.argus.ui.call.CallScreen
import com.example.argus.ui.chat.ChatScreen
import com.example.argus.ui.chat.ContactInfoScreen
import com.example.argus.ui.main.MainScreen
import com.example.argus.ui.security.SecurityVerificationScreen
import com.example.argus.ui.settings.SettingsScreen
import com.example.argus.ui.status.EphemeralStatusItem
import com.example.argus.ui.status.FullScreenStatusViewer
import com.example.argus.ui.vault.ArgusVaultScreen
import com.example.argus.ui.shield.ArgusShieldScreen
import kotlinx.coroutines.launch

sealed class Screen {
    object Welcome : Screen()
    object Auth : Screen()
    object Main : Screen()
    data class Chat(val conversationId: String) : Screen()
    data class ContactInfo(val contact: Contact) : Screen()
    data class StatusViewer(val status: EphemeralStatusItem) : Screen()
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

    Box(modifier = Modifier.fillMaxSize().background(ObsidianBlack)) {
        when (val screen = currentScreen) {
            is Screen.Welcome -> {
                WelcomeScreen(
                    onGetStartedClick = { currentScreen = Screen.Auth }
                )
            }

            is Screen.Auth -> {
                var isLoading by remember { mutableStateOf(false) }
                var errorMsg by remember { mutableStateOf<String?>(null) }

                ArgusAuthScreen(
                    onLogin = { username, password ->
                        isLoading = true
                        errorMsg = null
                        coroutineScope.launch {
                            val res = container.authRepository.login(username, password)
                            isLoading = false
                            if (res.isSuccess) {
                                currentScreen = Screen.Main
                            } else {
                                errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Login failed. Check your username and password."
                            }
                        }
                    },
                    onRegister = { username, password, displayName ->
                        isLoading = true
                        errorMsg = null
                        coroutineScope.launch {
                            val res = container.authRepository.register(username, password, displayName)
                            isLoading = false
                            if (res.isSuccess) {
                                currentScreen = Screen.Main
                            } else {
                                errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Registration failed."
                            }
                        }
                    },
                    onResetPassword = { username, newPassword, recoveryKey ->
                        isLoading = true
                        errorMsg = null
                        coroutineScope.launch {
                            val res = container.authRepository.resetPassword(username, newPassword, recoveryKey)
                            isLoading = false
                            if (res.isSuccess) {
                                currentScreen = Screen.Main
                            } else {
                                errorMsg = res.exceptionOrNull()?.localizedMessage ?: "Password reset failed. Check your details."
                            }
                        }
                    },
                    onCheckUsername = { username ->
                        container.authRepository.checkUsernameAvailability(username)
                    },
                    isLoading = isLoading,
                    errorMessage = errorMsg
                )
            }

            is Screen.Main -> {
                MainScreen(
                    conversations = conversations,
                    contacts = contacts,
                    calls = calls,
                    authRepository = container.authRepository,
                    onConversationClick = { convId ->
                        currentScreen = Screen.Chat(convId)
                    },
                    onContactClick = { contact ->
                        coroutineScope.launch {
                            val convId = container.authRepository.startConversationWithUser(
                                User(
                                    id = contact.userId,
                                    username = contact.username ?: "",
                                    displayName = contact.displayName,
                                    phoneNumber = contact.phoneNumber
                                )
                            )
                            currentScreen = Screen.Chat(convId)
                        }
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
                    onAiAssistantClick = { currentScreen = Screen.AiAssistant },
                    onViewStatus = { status -> currentScreen = Screen.StatusViewer(status) }
                )
            }

            is Screen.StatusViewer -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                FullScreenStatusViewer(
                    status = screen.status,
                    onClose = { currentScreen = Screen.Main }
                )
            }

            is Screen.ContactInfo -> {
                BackHandler {
                    currentScreen = Screen.Main
                }
                ContactInfoScreen(
                    contact = screen.contact,
                    onBackClick = { currentScreen = Screen.Main },
                    onStartMessageClick = {
                        coroutineScope.launch {
                            val convId = container.authRepository.startConversationWithUser(
                                User(
                                    id = screen.contact.userId,
                                    username = screen.contact.username ?: "",
                                    displayName = screen.contact.displayName,
                                    phoneNumber = screen.contact.phoneNumber
                                )
                            )
                            currentScreen = Screen.Chat(convId)
                        }
                    },
                    onStartAudioCallClick = {
                        container.callRepository.initiateCall(
                            peerId = screen.contact.userId,
                            peerName = screen.contact.displayName,
                            peerAvatar = screen.contact.avatarUrl,
                            callType = CallType.VOICE
                        )
                        currentScreen = Screen.Call
                    },
                    onStartVideoCallClick = {
                        container.callRepository.initiateCall(
                            peerId = screen.contact.userId,
                            peerName = screen.contact.displayName,
                            peerAvatar = screen.contact.avatarUrl,
                            callType = CallType.VIDEO
                        )
                        currentScreen = Screen.Call
                    },
                    onVerifySafetyNumberClick = {
                        currentScreen = Screen.SecurityVerify(
                            peerName = screen.contact.displayName,
                            peerUserId = screen.contact.userId,
                            peerIdentityKey = screen.contact.identityKeyBase64 ?: "",
                            isVerified = screen.contact.isVerified
                        )
                    }
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
                            peerIdentityKey = contact?.identityKeyBase64 ?: "",
                            isVerified = contact?.isVerified ?: false
                        )
                    },
                    onContactInfoClick = {
                        val contact = contacts.firstOrNull { it.userId == recipientId } ?: Contact(
                            id = "c_$recipientId",
                            userId = recipientId,
                            displayName = conv.title,
                            phoneNumber = "+1 555 000 0000"
                        )
                        currentScreen = Screen.ContactInfo(contact)
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
