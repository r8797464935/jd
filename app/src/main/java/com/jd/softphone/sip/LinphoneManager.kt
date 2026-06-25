package com.jd.softphone.sip

import android.content.Context
import android.util.Log
import com.jd.softphone.data.SipAccount
import com.jd.softphone.data.SipTransport
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.linphone.core.Account
import org.linphone.core.AudioDevice
import org.linphone.core.Call
import org.linphone.core.Core
import org.linphone.core.CoreListenerStub
import org.linphone.core.Factory
import org.linphone.core.RegistrationState
import org.linphone.core.TransportType
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Owns the liblinphone [Core] and exposes registration + call state as flows.
 *
 * This is the single source of truth for SIP. It is created once (Hilt @Singleton)
 * and its lifecycle is driven by [SipService] so the Core survives in the background.
 */
@Singleton
class LinphoneManager @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val _registration = MutableStateFlow(RegistrationStatus.NONE)
    val registration: StateFlow<RegistrationStatus> = _registration.asStateFlow()

    private val _call = MutableStateFlow(CallUiState())
    val call: StateFlow<CallUiState> = _call.asStateFlow()

    private var core: Core? = null

    private val listener = object : CoreListenerStub() {
        override fun onAccountRegistrationStateChanged(
            core: Core,
            account: Account,
            state: RegistrationState,
            message: String,
        ) {
            Log.d(TAG, "Registration state: $state ($message)")
            _registration.value = when (state) {
                RegistrationState.Progress -> RegistrationStatus.PROGRESS
                RegistrationState.Ok -> RegistrationStatus.REGISTERED
                RegistrationState.Cleared -> RegistrationStatus.CLEARED
                RegistrationState.Failed -> RegistrationStatus.FAILED
                else -> _registration.value
            }
        }

        override fun onCallStateChanged(
            core: Core,
            call: Call,
            state: Call.State,
            message: String,
        ) {
            Log.d(TAG, "Call state: $state ($message)")
            when (state) {
                Call.State.OutgoingInit,
                Call.State.OutgoingProgress,
                Call.State.OutgoingRinging,
                Call.State.OutgoingEarlyMedia -> updateCall(call, CallPhase.OUTGOING)

                Call.State.IncomingReceived,
                Call.State.IncomingEarlyMedia -> updateCall(call, CallPhase.INCOMING)

                Call.State.Connected,
                Call.State.StreamsRunning -> updateCall(call, CallPhase.CONNECTED)

                Call.State.End,
                Call.State.Released -> _call.value = CallUiState(phase = CallPhase.ENDED)

                Call.State.Error ->
                    _call.value = CallUiState(phase = CallPhase.ENDED, errorMessage = message)

                else -> Unit
            }
        }
    }

    /** Create and start the Core. Safe to call repeatedly; subsequent calls are no-ops. */
    fun start() {
        if (core != null) return

        val core = Factory.instance().createCore(null, null, context).also { this.core = it }
        core.addListener(listener)
        // Let liblinphone drive its own iterate() loop on a background thread.
        core.isAutoIterateEnabled = true
        // Enable echo cancellation; liblinphone ships a software AEC.
        core.isEchoCancellationEnabled = true
        core.start()
        Log.i(TAG, "Linphone Core started")
    }

    /** Register (or re-register) the given SIP account, replacing any existing one. */
    fun configureAccount(account: SipAccount) {
        val core = core ?: return
        clearAccountsInternal(core)

        val identity = Factory.instance().createAddress(account.identity)
            ?: run {
                _registration.value = RegistrationStatus.FAILED
                return
            }
        if (account.displayName.isNotBlank()) identity.displayName = account.displayName

        val authInfo = Factory.instance().createAuthInfo(
            account.username, null, account.password, null, null, account.domain,
        )
        core.addAuthInfo(authInfo)

        val params = core.createAccountParams()
        params.identityAddress = identity

        val serverAddress = Factory.instance().createAddress("sip:${account.domain}")
        serverAddress?.transport = account.transport.toLinphone()
        params.serverAddress = serverAddress
        params.isRegisterEnabled = true

        val linphoneAccount = core.createAccount(params)
        core.addAccount(linphoneAccount)
        core.defaultAccount = linphoneAccount
        _registration.value = RegistrationStatus.PROGRESS
    }

    /** Unregister and forget the current account. */
    fun clearAccount() {
        val core = core ?: return
        clearAccountsInternal(core)
        _registration.value = RegistrationStatus.NONE
    }

    private fun clearAccountsInternal(core: Core) {
        core.defaultAccount?.let { account ->
            // Gracefully unregister before removing.
            val params = account.params.clone()
            params.isRegisterEnabled = false
            account.params = params
        }
        core.clearAccounts()
        core.clearAllAuthInfo()
    }

    // ---- Call control ----

    fun call(remote: String, defaultDomain: String?) {
        val core = core ?: return
        val address = parseRemote(remote, defaultDomain) ?: return
        val params = core.createCallParams(null)
        params?.isAudioEnabled = true
        if (params != null) core.inviteAddressWithParams(address, params) else core.inviteAddress(address)
    }

    fun answer() {
        core?.currentCall?.accept()
    }

    fun hangUp() {
        val core = core ?: return
        core.currentCall?.terminate() ?: core.terminateAllCalls()
    }

    fun setMuted(muted: Boolean) {
        val core = core ?: return
        core.currentCall?.microphoneMuted = muted
        _call.update { it.copy(isMuted = muted) }
    }

    fun setSpeaker(on: Boolean) {
        val core = core ?: return
        val call = core.currentCall ?: return
        val targetType = if (on) AudioDevice.Type.Speaker else AudioDevice.Type.Earpiece
        core.audioDevices
            .firstOrNull { it.hasCapability(AudioDevice.Capabilities.CapabilityPlay) && it.type == targetType }
            ?.let { call.outputAudioDevice = it }
        _call.update { it.copy(isSpeakerOn = on) }
    }

    fun sendDtmf(digit: Char) {
        core?.currentCall?.sendDtmf(digit)
    }

    private fun updateCall(call: Call, phase: CallPhase) {
        val remote = call.remoteAddress
        _call.update {
            it.copy(
                phase = phase,
                remoteAddress = remote.asStringUriOnly(),
                remoteDisplayName = remote.displayName ?: remote.username ?: "",
                isMuted = call.microphoneMuted,
                durationSeconds = call.duration,
            )
        }
    }

    private fun parseRemote(input: String, defaultDomain: String?) =
        when {
            input.startsWith("sip:") -> Factory.instance().createAddress(input)
            input.contains("@") -> Factory.instance().createAddress("sip:$input")
            !defaultDomain.isNullOrBlank() ->
                Factory.instance().createAddress("sip:$input@$defaultDomain")
            else -> Factory.instance().createAddress("sip:$input")
        }

    private fun SipTransport.toLinphone() = when (this) {
        SipTransport.UDP -> TransportType.Udp
        SipTransport.TCP -> TransportType.Tcp
        SipTransport.TLS -> TransportType.Tls
    }

    private companion object {
        const val TAG = "LinphoneManager"
    }
}
