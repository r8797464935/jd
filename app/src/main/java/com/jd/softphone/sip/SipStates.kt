package com.jd.softphone.sip

/** Registration status surfaced to the UI. */
enum class RegistrationStatus {
    NONE,        // no account configured
    PROGRESS,    // registering / refreshing
    REGISTERED,  // online, ready for calls
    FAILED,      // registration error (auth, network, server)
    CLEARED,     // unregistered
}

/** High-level phase of the current call, mapped from Linphone's Call.State. */
enum class CallPhase {
    IDLE,
    OUTGOING,    // we are calling out (ringing on the remote side)
    INCOMING,    // remote party is calling us
    CONNECTED,   // media is flowing
    ENDED,       // released / error
}

/** Snapshot of the active call for the UI. */
data class CallUiState(
    val phase: CallPhase = CallPhase.IDLE,
    val remoteAddress: String = "",
    val remoteDisplayName: String = "",
    val isMuted: Boolean = false,
    val isSpeakerOn: Boolean = false,
    val durationSeconds: Int = 0,
    val errorMessage: String? = null,
) {
    val isActive: Boolean
        get() = phase != CallPhase.IDLE && phase != CallPhase.ENDED
}
