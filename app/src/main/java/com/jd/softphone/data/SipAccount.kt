package com.jd.softphone.data

/** Transport used for SIP signaling. TLS is strongly preferred for security. */
enum class SipTransport { UDP, TCP, TLS }

/** A SIP account the app registers with. */
data class SipAccount(
    val username: String,
    val password: String,
    val domain: String,
    val displayName: String = "",
    val transport: SipTransport = SipTransport.TLS,
) {
    val isValid: Boolean
        get() = username.isNotBlank() && password.isNotBlank() && domain.isNotBlank()

    /** e.g. sip:alice@example.com */
    val identity: String
        get() = "sip:$username@$domain"
}
