package com.jd.softphone.ui.settings

import androidx.lifecycle.ViewModel
import com.jd.softphone.data.SipAccount
import com.jd.softphone.data.SipAccountStore
import com.jd.softphone.data.SipTransport
import com.jd.softphone.sip.LinphoneManager
import com.jd.softphone.sip.RegistrationStatus
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject

data class SettingsForm(
    val username: String = "",
    val password: String = "",
    val domain: String = "",
    val displayName: String = "",
    val transport: SipTransport = SipTransport.TLS,
) {
    fun toAccount() = SipAccount(username.trim(), password, domain.trim(), displayName.trim(), transport)
    val canSave: Boolean get() = toAccount().isValid
}

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val accountStore: SipAccountStore,
    private val linphoneManager: LinphoneManager,
) : ViewModel() {

    private val _form = MutableStateFlow(accountStore.account.value?.toForm() ?: SettingsForm())
    val form: StateFlow<SettingsForm> = _form.asStateFlow()

    val registration: StateFlow<RegistrationStatus> = linphoneManager.registration

    fun onUsername(v: String) = _form.update { it.copy(username = v) }
    fun onPassword(v: String) = _form.update { it.copy(password = v) }
    fun onDomain(v: String) = _form.update { it.copy(domain = v) }
    fun onDisplayName(v: String) = _form.update { it.copy(displayName = v) }
    fun onTransport(v: SipTransport) = _form.update { it.copy(transport = v) }

    fun save() {
        val account = _form.value.toAccount()
        if (!account.isValid) return
        accountStore.save(account)
        linphoneManager.configureAccount(account)
    }

    fun clear() {
        accountStore.clear()
        linphoneManager.clearAccount()
        _form.value = SettingsForm()
    }

    private fun SipAccount.toForm() =
        SettingsForm(username, password, domain, displayName, transport)
}
