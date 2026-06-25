package com.jd.softphone.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jd.softphone.R
import com.jd.softphone.data.SipTransport
import com.jd.softphone.sip.RegistrationStatus

@Composable
fun SettingsScreen(viewModel: SettingsViewModel = hiltViewModel()) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val registration by viewModel.registration.collectAsStateWithLifecycle()
    var showPassword by remember { mutableStateOf(false) }

    Column(
        Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(stringResource(R.string.settings_title), style = androidx.compose.material3.MaterialTheme.typography.headlineSmall)
        RegistrationBadge(registration)

        OutlinedTextField(
            value = form.username,
            onValueChange = viewModel::onUsername,
            label = { Text(stringResource(R.string.settings_username)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.password,
            onValueChange = viewModel::onPassword,
            label = { Text(stringResource(R.string.settings_password)) },
            singleLine = true,
            visualTransformation =
                if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Hide" else "Show")
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.domain,
            onValueChange = viewModel::onDomain,
            label = { Text(stringResource(R.string.settings_domain)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = form.displayName,
            onValueChange = viewModel::onDisplayName,
            label = { Text(stringResource(R.string.settings_display_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )

        Text(stringResource(R.string.settings_transport))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SipTransport.entries.forEach { transport ->
                FilterChip(
                    selected = form.transport == transport,
                    onClick = { viewModel.onTransport(transport) },
                    label = { Text(transport.name) },
                )
            }
        }

        Button(
            onClick = viewModel::save,
            enabled = form.canSave,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_save)) }

        OutlinedButton(
            onClick = viewModel::clear,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.settings_clear)) }
    }
}

@Composable
private fun RegistrationBadge(status: RegistrationStatus) {
    val text = when (status) {
        RegistrationStatus.REGISTERED -> "● Registered"
        RegistrationStatus.PROGRESS -> "● Registering…"
        RegistrationStatus.FAILED -> "● Registration failed"
        RegistrationStatus.CLEARED -> "● Unregistered"
        RegistrationStatus.NONE -> "● No account"
    }
    Text(text, style = androidx.compose.material3.MaterialTheme.typography.labelLarge)
}
