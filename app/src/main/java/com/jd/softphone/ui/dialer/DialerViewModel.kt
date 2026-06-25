package com.jd.softphone.ui.dialer

import androidx.lifecycle.ViewModel
import com.jd.softphone.data.SipAccountStore
import com.jd.softphone.sip.LinphoneManager
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject

@HiltViewModel
class DialerViewModel @Inject constructor(
    private val linphoneManager: LinphoneManager,
    private val accountStore: SipAccountStore,
) : ViewModel() {

    private val _input = MutableStateFlow("")
    val input: StateFlow<String> = _input.asStateFlow()

    fun append(digit: Char) {
        _input.value += digit
    }

    fun backspace() {
        _input.value = _input.value.dropLast(1)
    }

    fun clear() {
        _input.value = ""
    }

    fun call() {
        val target = _input.value.trim()
        if (target.isEmpty()) return
        linphoneManager.call(target, accountStore.account.value?.domain)
    }
}
