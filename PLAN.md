# Android SIP Softphone — Project Plan

An open-source Android softphone (like X-Lite / Zoiper): a SIP/VoIP client that
registers with a SIP server and makes/receives voice calls over IP.

## Decisions (locked)

| Decision | Choice | Why |
|---|---|---|
| **SIP/media stack** | **Linphone SDK (liblinphone)** | Open-source AAR via Maven, codecs + echo cancel + ICE + **built-in push** included; fastest path to a working call. |
| **Licensing model** | **Open-source / hobby** | Linphone's GPLv3 is a perfect fit. No paid license needed. |
| **Killed-app incoming calls** | **Required for v1** | Linphone's native RFC 8599 push support is the main reason it beats PJSIP here. |
| **Fallback stack** | PJSIP (PJSUA2) | Documented escape hatch if we ever need a smaller footprint / deeper control. |

> Don't use `android.net.sip.SipManager` — it is deprecated/removed.

## Architecture

- **Language / UI:** Kotlin + Jetpack Compose
- **Pattern:** MVVM + Repository; coroutines + Flow
- **DI:** Hilt
- **Persistence:** Room (accounts, call log, cached contacts); DataStore (settings)
- **Telephony integration:** `android.telecom` **self-managed ConnectionService** so the
  app behaves like a real phone (in-call audio routing, lock-screen UI, coexists with
  cellular calls)
- **Background:** a **foreground service** holds the SIP registration + active call
- **Killed-app incoming calls:** **FCM** high-priority push (RFC 8599 SIP push) wakes the
  app to a full-screen incoming-call UI

### Required permissions
`INTERNET`, `RECORD_AUDIO`, `FOREGROUND_SERVICE`, `FOREGROUND_SERVICE_MICROPHONE`,
`FOREGROUND_SERVICE_PHONE_CALL`, `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT`,
`MANAGE_OWN_CALLS`, `BLUETOOTH_CONNECT`, `ACCESS_NETWORK_STATE`.

## Build phases

### Phase 0 — Project skeleton
- Android Studio project, min SDK 26, target latest; Compose, Hilt, Room, Navigation
- Declare permissions above
- GitHub Actions CI building a debug APK

### Phase 1 — SIP account & registration
- Settings screen: username, password, domain/proxy, transport (UDP/TCP/**TLS**), port
- Integrate Linphone SDK; REGISTER; surface registration state (online/offline/error)
- Store credentials encrypted (Android Keystore / EncryptedSharedPreferences)

### Phase 2 — Outgoing calls
- Dialpad + in-call screen (mute, speaker, hang up, DTMF, call timer)
- Place a call; negotiate audio; confirm two-way audio
- Audio: `AudioManager` `MODE_IN_COMMUNICATION`, audio focus, `AcousticEchoCanceler`,
  earpiece / speaker / Bluetooth SCO routing

### Phase 3 — Incoming calls (app running / backgrounded)
- Foreground service keeps registration alive
- Incoming INVITE → ringing UI, accept/reject, ringtone + vibrate
- ConnectionService integration for lock-screen behavior

### Phase 4 — Incoming calls when app is KILLED (the hard part)
- FCM integration; register push token with SIP server via RFC 8599 `pn-*` contact params
  (or stand up a small push gateway if the server lacks native support)
- High-priority push → full-screen intent incoming-call notification
- **Server dependency:** needs a SIP server that supports push (FreeSWITCH/Asterisk module
  or a gateway) — plan/test this early

### Phase 5 — NAT, quality & security
- STUN / TURN / ICE for NAT traversal
- **SIP over TLS** + **SRTP / ZRTP** media encryption
- Codecs: Opus, G.722, G.711 µ/A-law (all license-free); jitter buffer, packet-loss handling
- Re-register on network change (Wi-Fi ↔ cellular)

### Phase 6 — App polish
- Call history (Room), contacts integration, multiple SIP accounts
- Hold, blind/attended transfer, mute-on-route-change
- Theming, onboarding, error states, battery-optimization guidance for the user

### Phase 7 — (Optional) Video calling
- VP8 / H.264 codecs, camera capture, render surfaces, bandwidth adaptation

## External requirements (not in the app)
- A **SIP account / server** to test against: a hosted ITSP, or self-hosted
  **FreeSWITCH** / **Asterisk (FreePBX)**.
- For killed-app push: server-side push support **or** a small push gateway.

## Top risks / gotchas
- **Killed-app push** depends on the *server*, not just the app — the #1 Android softphone
  pain point. Validate the server's push path before building Phase 4 UI.
- **Echo cancellation & audio routing** (esp. Bluetooth) is fiddly — use the stack's AEC.
- **Doze / battery optimization** can kill registration — foreground service + push is the
  standard mitigation; also guide users to whitelist the app.
- **Codec licensing** — stick to Opus / VP8 / G.711 / G.722 to stay license-free; avoid
  G.729 / H.264 unless licensing is handled.

## Suggested first milestone
Phases 0–2: a build that **registers and places an outgoing call with two-way audio**.
That proves the stack end-to-end and is the natural first demo.
