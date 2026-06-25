package com.jd.softphone.ui.incall

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.CallEnd
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.jd.softphone.sip.CallPhase
import com.jd.softphone.sip.CallUiState

@Composable
fun InCallScreen(
    state: CallUiState,
    onAnswer: () -> Unit,
    onHangUp: () -> Unit,
    onToggleMute: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onDtmf: (Char) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        modifier = Modifier.fillMaxSize(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.size(64.dp))
            Text(
                text = state.remoteDisplayName.ifEmpty { state.remoteAddress.ifEmpty { "Unknown" } },
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.size(8.dp))
            Text(
                text = statusLabel(state),
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )

            Spacer(Modifier.weight(1f))

            when (state.phase) {
                CallPhase.INCOMING -> IncomingControls(onAnswer = onAnswer, onDecline = onHangUp)
                else -> ActiveControls(
                    state = state,
                    onToggleMute = onToggleMute,
                    onToggleSpeaker = onToggleSpeaker,
                    onHangUp = onHangUp,
                )
            }
            Spacer(Modifier.size(24.dp))
        }
    }
}

@Composable
private fun ActiveControls(
    state: CallUiState,
    onToggleMute: (Boolean) -> Unit,
    onToggleSpeaker: (Boolean) -> Unit,
    onHangUp: () -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        RoundToggle(
            icon = if (state.isMuted) Icons.Default.MicOff else Icons.Default.Mic,
            label = "Mute",
            active = state.isMuted,
            onClick = { onToggleMute(!state.isMuted) },
        )
        RoundToggle(
            icon = Icons.Default.VolumeUp,
            label = "Speaker",
            active = state.isSpeakerOn,
            onClick = { onToggleSpeaker(!state.isSpeakerOn) },
        )
    }
    Spacer(Modifier.size(32.dp))
    HangUpButton(onHangUp)
}

@Composable
private fun IncomingControls(onAnswer: () -> Unit, onDecline: () -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
    ) {
        CircleButton(
            icon = Icons.Default.CallEnd,
            background = MaterialTheme.colorScheme.error,
            tint = MaterialTheme.colorScheme.onError,
            onClick = onDecline,
        )
        CircleButton(
            icon = Icons.Default.Call,
            background = Color(0xFF1DB954),
            tint = Color.White,
            onClick = onAnswer,
        )
    }
}

@Composable
private fun HangUpButton(onHangUp: () -> Unit) {
    CircleButton(
        icon = Icons.Default.CallEnd,
        background = MaterialTheme.colorScheme.error,
        tint = MaterialTheme.colorScheme.onError,
        onClick = onHangUp,
    )
}

@Composable
private fun CircleButton(
    icon: ImageVector,
    background: Color,
    tint: Color,
    onClick: () -> Unit,
) {
    Surface(
        color = background,
        shape = CircleShape,
        onClick = onClick,
        modifier = Modifier.size(72.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(icon, contentDescription = null, tint = tint)
        }
    }
}

@Composable
private fun RoundToggle(
    icon: ImageVector,
    label: String,
    active: Boolean,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Surface(
            color = if (active) MaterialTheme.colorScheme.primary
            else MaterialTheme.colorScheme.surfaceVariant,
            shape = CircleShape,
            onClick = onClick,
            modifier = Modifier.size(56.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    icon,
                    contentDescription = label,
                    tint = if (active) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Spacer(Modifier.size(4.dp))
        Text(label, style = MaterialTheme.typography.labelSmall)
    }
}

private fun statusLabel(state: CallUiState): String = when (state.phase) {
    CallPhase.OUTGOING -> "Calling…"
    CallPhase.INCOMING -> "Incoming call"
    CallPhase.CONNECTED -> formatDuration(state.durationSeconds)
    CallPhase.ENDED -> state.errorMessage ?: "Call ended"
    CallPhase.IDLE -> ""
}

private fun formatDuration(seconds: Int): String {
    val m = seconds / 60
    val s = seconds % 60
    return "%02d:%02d".format(m, s)
}
