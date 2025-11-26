package com.coparently.app.presentation.pairing

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.coparently.app.data.analytics.AnalyticsManager
import com.coparently.app.data.remote.firebase.CoParentPairingService
import com.coparently.app.data.remote.firebase.FirebaseAuthService
import com.coparently.app.data.remote.firebase.QRCodeService
import com.coparently.app.domain.model.Conversation
import com.coparently.app.domain.repository.MessageRepository
import com.coparently.app.domain.repository.UserRepository
import com.coparently.app.utils.ValidationResult
import com.coparently.app.utils.ValidationUtils
import java.time.LocalDateTime
import java.util.UUID
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.TimeoutCancellationException
import javax.inject.Inject

/**
 * ViewModel for pairing screen.
 * Управляет состоянием pairing и приглашений co-parent.
 *
 * Обновлен для использования ValidationUtils для валидации email.
 */
@HiltViewModel
class PairingViewModel @Inject constructor(
    private val pairingService: CoParentPairingService,
    private val firebaseAuthService: FirebaseAuthService,
    private val userRepository: UserRepository,
    private val messageRepository: MessageRepository,
    private val analyticsManager: AnalyticsManager,
    private val qrCodeService: QRCodeService
) : ViewModel() {

    private val _uiState = MutableStateFlow(PairingUiState())
    val uiState: StateFlow<PairingUiState> = _uiState.asStateFlow()

    init {
        loadPairingInfo()
        loadPendingInvitations()
    }

    private fun loadPairingInfo() {
        viewModelScope.launch {
            val currentUser = userRepository.getCurrentUser()
            currentUser?.partnerId?.let { partnerId ->
                val partnerInfo = pairingService.getPartnerInfo(partnerId)
                _uiState.value = _uiState.value.copy(
                    partnerEmail = partnerInfo?.get("email") as? String
                )
            }
        }
    }

    private fun loadPendingInvitations() {
        viewModelScope.launch {
            val currentUser = firebaseAuthService.getCurrentUser()
            if (currentUser != null) {
                val result = pairingService.getPendingInvitations(currentUser.email ?: "")
                result.fold(
                    onSuccess = { invitations ->
                        _uiState.value = _uiState.value.copy(pendingInvitations = invitations)
                    },
                    onFailure = { /* Handle error */ }
                )
            }
        }
    }

    fun updateInvitationEmail(email: String) {
        _uiState.value = _uiState.value.copy(
            invitationEmail = email,
            errorMessage = null,
            emailError = null
        )
    }

    fun sendInvitation(onSuccess: () -> Unit) {
        val state = _uiState.value

        // Валидация email перед отправкой
        val emailValidation = ValidationUtils.validateEmail(state.invitationEmail)
        if (emailValidation is ValidationResult.Error) {
            _uiState.value = state.copy(
                emailError = emailValidation.message,
                errorMessage = null
            )
            return
        }

        // Дополнительная проверка - нельзя отправить приглашение самому себе
        val currentUserEmail = firebaseAuthService.getCurrentUser()?.email
        if (state.invitationEmail.equals(currentUserEmail, ignoreCase = true)) {
            _uiState.value = state.copy(
                emailError = "You cannot invite yourself",
                errorMessage = null
            )
            return
        }

        _uiState.value = state.copy(
            isLoading = true,
            errorMessage = null,
            emailError = null
        )

        viewModelScope.launch {
            try {
                // Wait for authentication to be ready (up to 5 seconds)
                val currentUser = firebaseAuthService.waitForAuthReady()

                if (currentUser == null) {
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = "User not authenticated. Please sign in."
                    )
                    return@launch
                }

                // Sync user data from Firestore before getting current user
                // This ensures user data exists in local database
                // Use timeout to prevent hanging (increased to 30 seconds for first connection)
                try {
                    withTimeoutOrNull(30000) {
                        userRepository.syncWithFirestore()
                    } ?: android.util.Log.w("PairingViewModel", "Sync with Firestore timed out")
                } catch (e: Exception) {
                    // Check if it's a NOT_FOUND error (database not created)
                    if (e.message?.contains("NOT_FOUND") == true ||
                        e.message?.contains("database (default) does not exist") == true) {
                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = "Firestore database is not created. Please create it:\nhttps://console.cloud.google.com/datastore/setup?project=coparently-a39c9"
                        )
                        return@launch
                    }
                    android.util.Log.w("PairingViewModel", "Failed to sync user data, trying to get from local DB", e)
                }

                var currentUserData = userRepository.getCurrentUser()

                // If user data still not found, try to create it from Firebase user
                if (currentUserData == null) {
                    android.util.Log.d("PairingViewModel", "User data not found in local DB, creating from Firebase user")
                    try {
                        // Use timeout to prevent hanging on Firestore operations (increased to 20 seconds)
                        val firestoreData = withTimeoutOrNull(20000) {
                            pairingService.getPartnerInfo(currentUser.uid)
                        }

                        if (firestoreData != null) {
                            // User exists in Firestore, sync it (increased to 30 seconds)
                            try {
                                withTimeoutOrNull(30000) {
                                    userRepository.syncWithFirestore()
                                }
                            } catch (e: Exception) {
                                android.util.Log.w("PairingViewModel", "Failed to sync after getting Firestore data", e)
                            }
                            currentUserData = userRepository.getCurrentUser()
                        } else {
                            // User doesn't exist in Firestore or Firestore unavailable, create basic user profile
                            val newUser = com.coparently.app.domain.model.User(
                                id = currentUser.uid,
                                email = currentUser.email ?: "",
                                name = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "User",
                                role = "mom",
                                colorCode = "#FF4081"
                            )
                            userRepository.upsertUser(newUser)
                            currentUserData = userRepository.getCurrentUser()
                        }
                    } catch (e: Exception) {
                        // Check if it's a NOT_FOUND error (database not created)
                        if (e.message?.contains("NOT_FOUND") == true ||
                            e.message?.contains("database (default) does not exist") == true) {
                            _uiState.value = state.copy(
                                isLoading = false,
                                errorMessage = "Firestore database is not created. Please create it:\nhttps://console.cloud.google.com/datastore/setup?project=coparently-a39c9"
                            )
                            return@launch
                        }
                        // If Firestore is offline or unavailable, create user from Firebase auth data
                        android.util.Log.w("PairingViewModel", "Firestore unavailable, creating user from Firebase auth", e)
                        val newUser = com.coparently.app.domain.model.User(
                            id = currentUser.uid,
                            email = currentUser.email ?: "",
                            name = currentUser.displayName ?: currentUser.email?.substringBefore("@") ?: "User",
                            role = "mom",
                            colorCode = "#FF4081"
                        )
                        userRepository.upsertUser(newUser)
                        currentUserData = userRepository.getCurrentUser()
                    }
                }

                if (currentUserData == null) {
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = "User data not found. Please try again."
                    )
                    return@launch
                }

                // Send invitation with timeout to prevent hanging (increased to 30 seconds)
                val result = withTimeoutOrNull(30000) {
                    pairingService.sendInvitation(
                        currentUser.uid,
                        currentUser.email ?: "",
                        currentUserData.name,
                        state.invitationEmail
                    )
                }

                if (result == null) {
                    // Timeout occurred
                    _uiState.value = state.copy(
                        isLoading = false,
                        errorMessage = "Request timed out. Please check your internet connection and try again."
                    )
                    return@launch
                }

                result.fold(
                    onSuccess = {
                        analyticsManager.logInvitationSent()
                        android.util.Log.d("PairingViewModel", "Invitation sent successfully")
                        _uiState.value = state.copy(
                            isLoading = false,
                            invitationEmail = "", // Clear the email field
                            errorMessage = null
                        )
                        onSuccess()
                    },
                    onFailure = { error ->
                        // Log full error for debugging
                        android.util.Log.e("PairingViewModel", "Failed to send invitation", error)
                        android.util.Log.e("PairingViewModel", "Error type: ${error.javaClass.simpleName}")
                        android.util.Log.e("PairingViewModel", "Error message: ${error.message}")

                        // Check for specific Firestore errors
                        val errorMessage = when {
                            error.message?.contains("NOT_FOUND") == true ||
                            error.message?.contains("database (default) does not exist") == true ||
                            error.message?.contains("add a Cloud Datastore or Cloud Firestore database") == true -> {
                                "Firestore database is not created. Please create it:\nhttps://console.cloud.google.com/datastore/setup?project=coparently-a39c9"
                            }
                            error.message?.contains("PERMISSION_DENIED") == true ||
                            error.message?.contains("Cloud Firestore API has not been used") == true -> {
                                "Firestore API is not enabled. Please enable it in Google Cloud Console:\nhttps://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=coparently-a39c9"
                            }
                            error.message?.contains("UNAVAILABLE") == true ||
                            error.message?.contains("Unable to resolve host") == true ||
                            error.message?.contains("UnknownHostException") == true -> {
                                "No internet connection or DNS error. Please check your internet connection and try again."
                            }
                            error.message?.contains("offline") == true ||
                            error.message?.contains("client is offline") == true -> {
                                "No internet connection. Please check your connection and try again."
                            }
                            error.message?.contains("timeout") == true ||
                            error.message?.contains("timed out") == true -> {
                                "Request timed out. Please check your internet connection and try again."
                            }
                            else -> error.message ?: "Failed to send invitation"
                        }

                        _uiState.value = state.copy(
                            isLoading = false,
                            errorMessage = errorMessage
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = state.copy(
                    isLoading = false,
                    errorMessage = "Error: ${e.message}"
                )
            }
        }
    }

    fun acceptInvitation(invitationId: String) {
        _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)

        viewModelScope.launch {
            try {
                val currentUser = firebaseAuthService.getCurrentUser()
                if (currentUser == null) {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "User not authenticated. Please sign in."
                    )
                    return@launch
                }

                // Sync user data before accepting invitation (increased to 30 seconds)
                try {
                    withTimeoutOrNull(30000) {
                        userRepository.syncWithFirestore()
                    }
                } catch (e: Exception) {
                    android.util.Log.w("PairingViewModel", "Failed to sync user data before accepting invitation", e)
                }

                // Get invitation details to know who we're pairing with (increased to 30 seconds)
                val pendingInvitationsResult = withTimeoutOrNull(30000) {
                    pairingService.getPendingInvitations(currentUser.email ?: "")
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Request timed out. Please check your internet connection and try again."
                    )
                    return@launch
                }

                val invitation = pendingInvitationsResult.getOrNull()?.firstOrNull { it["id"] == invitationId }
                val fromUserId = invitation?.get("fromUserId") as? String

                val result = withTimeoutOrNull(30000) {
                    pairingService.acceptInvitation(invitationId, currentUser.uid)
                } ?: run {
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        errorMessage = "Request timed out. Please check your internet connection and try again."
                    )
                    return@launch
                }
                result.fold(
                    onSuccess = {
                        analyticsManager.logInvitationAccepted()

                        // Sync user data again to get updated partnerId (increased to 30 seconds)
                        try {
                            withTimeoutOrNull(30000) {
                                userRepository.syncWithFirestore()
                            }
                        } catch (e: Exception) {
                            android.util.Log.w("PairingViewModel", "Failed to sync user data after accepting invitation", e)
                        }

                        // Create conversation for chat between the two parents
                        if (fromUserId != null) {
                            try {
                                val partnerInfo = pairingService.getPartnerInfo(fromUserId)
                                val partnerName = partnerInfo?.get("name") as? String ?: "Co-Parent"

                                // Create conversation - MessageRepository will handle duplicates
                                val conversation = Conversation(
                                    id = UUID.randomUUID().toString(),
                                    participants = listOf(currentUser.uid, fromUserId),
                                    title = partnerName,
                                    createdAt = LocalDateTime.now(),
                                    unreadCount = 0,
                                    syncedToFirestore = false
                                )
                                messageRepository.createConversation(conversation)
                                android.util.Log.d("PairingViewModel", "Created conversation with partner: $partnerName")
                            } catch (e: Exception) {
                                android.util.Log.e("PairingViewModel", "Failed to create conversation after pairing", e)
                                // Don't fail the pairing if conversation creation fails
                            }
                        }

                        loadPairingInfo()
                        loadPendingInvitations()
                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = null
                        )
                    },
                    onFailure = { error ->
                        // Check for specific Firestore errors
                        val errorMessage = when {
                            error.message?.contains("NOT_FOUND") == true ||
                            error.message?.contains("database (default) does not exist") == true ||
                            error.message?.contains("add a Cloud Datastore or Cloud Firestore database") == true -> {
                                "Firestore database is not created. Please create it:\nhttps://console.cloud.google.com/datastore/setup?project=coparently-a39c9"
                            }
                            error.message?.contains("PERMISSION_DENIED") == true ||
                            error.message?.contains("Cloud Firestore API has not been used") == true -> {
                                "Firestore API is not enabled. Please enable it in Google Cloud Console:\nhttps://console.developers.google.com/apis/api/firestore.googleapis.com/overview?project=coparently-a39c9"
                            }
                            error.message?.contains("UNAVAILABLE") == true ||
                            error.message?.contains("Unable to resolve host") == true ||
                            error.message?.contains("UnknownHostException") == true -> {
                                "No internet connection or DNS error. Please check your internet connection and try again."
                            }
                            error.message?.contains("offline") == true ||
                            error.message?.contains("client is offline") == true -> {
                                "No internet connection. Please check your connection and try again."
                            }
                            error.message?.contains("timeout") == true ||
                            error.message?.contains("timed out") == true -> {
                                "Request timed out. Please check your internet connection and try again."
                            }
                            else -> error.message ?: "Failed to accept invitation"
                        }

                        _uiState.value = _uiState.value.copy(
                            isLoading = false,
                            errorMessage = errorMessage
                        )
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Error accepting invitation: ${e.message}"
                )
            }
        }
    }

    fun rejectInvitation(invitationId: String) {
        viewModelScope.launch {
            val result = pairingService.rejectInvitation(invitationId)
            result.fold(
                onSuccess = {
                    loadPendingInvitations()
                },
                onFailure = { /* Handle error */ }
            )
        }
    }

    fun removePairing() {
        viewModelScope.launch {
            val currentUser = firebaseAuthService.getCurrentUser()
            val currentUserData = userRepository.getCurrentUser()
            if (currentUser != null && currentUserData?.partnerId != null) {
                val result = pairingService.removePartnership(
                    currentUser.uid,
                    currentUserData.partnerId
                )
                result.fold(
                    onSuccess = {
                        loadPairingInfo()
                    },
                    onFailure = { /* Handle error */ }
                )
            }
        }
    }

    /**
     * Generates a QR code for co-parent pairing invitation.
     */
    fun generateQRCode() {
        viewModelScope.launch {
            try {
                // Wait for authentication to be ready
                val currentUser = firebaseAuthService.waitForAuthReady()
                val currentUserData = userRepository.getCurrentUser()

                if (currentUser == null || currentUserData == null) {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "User not authenticated. Please sign in."
                    )
                    return@launch
                }

                // Generate invitation ID for QR code
                val invitationId = "qr_${currentUser.uid}_${System.currentTimeMillis()}"

                // Generate QR code bitmap
                val qrBitmap = qrCodeService.generatePairingQRCode(
                    invitationId = invitationId,
                    inviterName = currentUserData.name,
                    inviterEmail = currentUser.email ?: ""
                )

                if (qrBitmap != null) {
                    // QR code expires in 24 hours
                    val expirationTime = System.currentTimeMillis() + (24 * 60 * 60 * 1000)
                    _uiState.value = _uiState.value.copy(
                        qrCodeBitmap = qrBitmap,
                        showQRCodeDialog = true,
                        qrCodeExpirationTime = expirationTime,
                        errorMessage = null
                    )
                    analyticsManager.logInvitationSent() // Log QR code generation as invitation sent
                } else {
                    _uiState.value = _uiState.value.copy(
                        errorMessage = "Failed to generate QR code. Please try again."
                    )
                }
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Error generating QR code: ${e.message}"
                )
            }
        }
    }

    /**
     * Dismisses the QR code dialog.
     */
    fun dismissQRCodeDialog() {
        _uiState.value = _uiState.value.copy(
            showQRCodeDialog = false,
            qrCodeBitmap = null,
            qrCodeExpirationTime = null
        )
    }

    /**
     * Regenerates QR code when expired.
     */
    fun regenerateQRCode() {
        generateQRCode()
    }

    /**
     * Shows an error message in the UI.
     */
    fun showError(message: String) {
        _uiState.value = _uiState.value.copy(
            errorMessage = message,
            isLoading = false
        )
    }
}

/**
 * UI state for pairing screen.
 *
 * @param invitationEmail Email для отправки приглашения
 * @param partnerEmail Email текущего партнера (если есть)
 * @param pendingInvitations Список ожидающих приглашений
 * @param isLoading Состояние загрузки
 * @param errorMessage Общее сообщение об ошибке
 * @param emailError Ошибка валидации email
 * @param qrCodeBitmap Generated QR code bitmap for sharing
 * @param showQRCodeDialog Whether to show the QR code sharing dialog
 * @param qrCodeExpirationTime Timestamp when QR code expires (24 hours from generation)
 */
data class PairingUiState(
    val invitationEmail: String = "",
    val partnerEmail: String? = null,
    val pendingInvitations: List<Map<String, Any?>> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val emailError: String? = null,
    val qrCodeBitmap: Bitmap? = null,
    val showQRCodeDialog: Boolean = false,
    val qrCodeExpirationTime: Long? = null
)

