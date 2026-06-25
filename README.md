# Softphone

An open-source Android **SIP softphone** (in the spirit of X-Lite / Zoiper): register
with a SIP server and make/receive VoIP calls. Built on the [Linphone SDK](https://www.linphone.org/)
(liblinphone) for signaling + media.

> See [PLAN.md](PLAN.md) for the full phased roadmap and design decisions.

## Status

Early development — **Phases 0–2** are scaffolded:

- ✅ **Phase 0** — Gradle/Compose/Hilt project skeleton, permissions, CI
- ✅ **Phase 1** — SIP account entry, encrypted credential storage, registration, foreground service
- ✅ **Phase 2** — Dialer, outgoing calls, in-call screen (mute / speaker / hang up), basic incoming-call UI

Later phases (push for killed-app calls, NAT/TLS/SRTP, call history, transfer, video) are
tracked in `PLAN.md`.

## Tech stack

| Concern | Choice |
|---|---|
| Language / UI | Kotlin + Jetpack Compose (Material 3) |
| Architecture | MVVM + Hilt DI, coroutines / Flow |
| SIP / media | Linphone SDK (`org.linphone:linphone-sdk-android`) |
| Credential storage | `EncryptedSharedPreferences` (Android Keystore) |
| Background | Foreground service holding the Linphone `Core` |

## Project layout

```
app/src/main/java/com/jd/softphone/
├─ SoftphoneApp.kt          # Hilt Application
├─ MainActivity.kt          # permissions + Compose host, starts SipService
├─ data/                    # SipAccount model + encrypted store
├─ sip/                     # LinphoneManager (Core wrapper), SipService, state models
└─ ui/                      # Compose: dialer, settings, in-call, theme
```

## Build

Requires JDK 17 and the Android SDK (`compileSdk 35`).

```bash
./gradlew assembleDebug
```

CI builds a debug APK on every push/PR (`.github/workflows/android.yml`).

## Try it

1. Install the debug APK on a device/emulator.
2. Open **Account**, enter your SIP username / password / domain, pick a transport
   (TLS recommended), and tap **Save & register**.
3. Once the status shows **Registered**, use the **Keypad** to place a call.

You need a SIP account to test against — a hosted VoIP/ITSP provider, or a self-hosted
**Asterisk/FreePBX** or **FreeSWITCH** server.

## License

The Linphone SDK is GPLv3 (or a commercial license from Belledonne Communications).
This project is intended as open source accordingly.
