package com.jd.softphone.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.jd.softphone.data.SipAccount
import com.jd.softphone.data.SipAccountStore
import com.jd.softphone.sip.CallUiState
import com.jd.softphone.sip.LinphoneManager
import com.jd.softphone.sip.RegistrationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/** Top-level state: registration badge + the active-call overlay and its controls. */
@HiltViewModel
class MainViewModel @Inject constructor(
    private val linphoneManager: LinphoneManager,
    accountStore: SipAccountStore,
) : ViewModel() {

    val registration: StateFlow<RegistrationStatus> = linphoneManager.registration
    val call: StateFlow<CallUiState> = linphoneManager.call

    val account: StateFlow<SipAccount?> = accountStore.account
        .stateIn(viewModelScope, SharingStarted.Eagerly, accountStore.account.value)

    fun answer() = linphoneManager.answer()
    fun hangUp() = linphoneManager.hangUp()
    fun toggleMute(muted: Boolean) = linphoneManager.setMuted(muted)
    fun toggleSpeaker(on: Boolean) = linphoneManager.setSpeaker(on)
    fun sendDtmf(digit: Char) = linphoneManager.sendDtmf(digit)
}
