package win.liuping.usque_android.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import win.liuping.usque_android.data.Settings
import win.liuping.usque_android.nativebridge.ErrorCode
import win.liuping.usque_android.ui.viewmodel.EnrollUiState
import win.liuping.usque_android.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(
    vm: SettingsViewModel = viewModel(),
    onReRegister: () -> Unit = {},
) {
    val settings by vm.settings.collectAsState()
    val config by vm.config.collectAsState()
    val enrollState by vm.enrollState.collectAsState()

    var showEnrollDialog by remember { mutableStateOf(false) }
    var showReRegisterDialog by remember { mutableStateOf(false) }

    if (showEnrollDialog) {
        EnrollDialog(
            enrollState = enrollState,
            onConfirm = { jwt -> vm.enroll(jwt) },
            onDismiss = {
                vm.resetEnrollState()
                showEnrollDialog = false
            },
        )
    }

    if (showReRegisterDialog) {
        AlertDialog(
            onDismissRequest = { showReRegisterDialog = false },
            title = { Text("Re-register account") },
            text = {
                Text("This will delete the current config and start a new registration. The existing account and keys will be lost.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        vm.clearConfig()
                        showReRegisterDialog = false
                        onReRegister()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Delete & Re-register")
                }
            },
            dismissButton = {
                TextButton(onClick = { showReRegisterDialog = false }) { Text("Cancel") }
            },
        )
    }

    LaunchedEffect(enrollState) {
        if (enrollState is EnrollUiState.Success) {
            showEnrollDialog = false
            vm.resetEnrollState()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        // ── Tunnel settings ──────────────────────────────────────────────
        SectionLabel("Tunnel")

        OutlinedTextField(
            value = settings.sni,
            onValueChange = { vm.update(settings.copy(sni = it)) },
            label = { Text("SNI override") },
            placeholder = { Text("consumer-masque.cloudflareclient.com") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = settings.dnsAddrs,
            onValueChange = { vm.update(settings.copy(dnsAddrs = it)) },
            label = { Text("DNS servers (comma-separated)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = settings.listenAddr,
            onValueChange = { vm.update(settings.copy(listenAddr = it)) },
            label = { Text("SOCKS5 listen address") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        OutlinedTextField(
            value = settings.mtu.toString(),
            onValueChange = { s -> s.toIntOrNull()?.let { vm.update(settings.copy(mtu = it)) } },
            label = { Text("MTU") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Use IPv6 endpoint")
            Switch(
                checked = settings.useIPv6,
                onCheckedChange = { vm.update(settings.copy(useIPv6 = it)) },
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Use HTTP/2 (TCP) instead of HTTP/3 (QUIC)")
            Switch(
                checked = settings.useHTTP2,
                onCheckedChange = { vm.update(settings.copy(useHTTP2 = it)) },
            )
        }

        // ── Config / Endpoint overrides ──────────────────────────────────
        SectionLabel("Config overrides")

        ConfigTextField(
            label = "Endpoint IPv4 (endpoint_v4)",
            value = config.endpoint_v4,
            onSave = { vm.updateConfigField("endpoint_v4", it) },
        )

        ConfigTextField(
            label = "Endpoint IPv6 (endpoint_v6)",
            value = config.endpoint_v6,
            onSave = { vm.updateConfigField("endpoint_v6", it) },
        )

        ConfigTextField(
            label = "Endpoint H2 IPv4 (endpoint_h2_v4)",
            value = config.endpoint_h2_v4,
            onSave = { vm.updateConfigField("endpoint_h2_v4", it) },
        )

        ConfigTextField(
            label = "Endpoint H2 IPv6 (endpoint_h2_v6)",
            value = config.endpoint_h2_v6,
            onSave = { vm.updateConfigField("endpoint_h2_v6", it) },
        )

        // ── Enroll ───────────────────────────────────────────────────────
        SectionLabel("Account")

        OutlinedButton(
            onClick = { showEnrollDialog = true },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Re-enroll device key")
        }
        Text(
            "Re-enroll refreshes IP addresses and endpoint info from Cloudflare servers. Useful for ZeroTrust where IPv6 may change.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(4.dp))

        OutlinedButton(
            onClick = { showReRegisterDialog = true },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.error),
        ) {
            Text("Re-register (delete account)")
        }
        Text(
            "Delete current config and register a new account from scratch.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp),
    )
    HorizontalDivider()
}

@Composable
private fun ConfigTextField(
    label: String,
    value: String,
    onSave: (String) -> Unit,
) {
    var text by remember(value) { mutableStateOf(value) }
    val changed = text != value

    OutlinedTextField(
        value = text,
        onValueChange = { text = it },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        trailingIcon = {
            if (changed) {
                TextButton(onClick = { onSave(text) }) { Text("Save") }
            }
        },
    )
}

@Composable
private fun EnrollDialog(
    enrollState: EnrollUiState,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var jwt by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = { if (enrollState !is EnrollUiState.Loading) onDismiss() },
        title = { Text("Re-enroll device key") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "This will re-enroll the existing private key with Cloudflare and refresh IP addresses.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    "For ZeroTrust, provide a team token (JWT). Leave empty for personal WARP.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = jwt,
                    onValueChange = { jwt = it },
                    label = { Text("Team token (optional)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                when (val s = enrollState) {
                    is EnrollUiState.Loading -> {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(Modifier.size(20.dp))
                            Text("Enrolling…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    is EnrollUiState.Error -> {
                        val msg = when (s.code) {
                            ErrorCode.NETWORK -> "Network error. Check your connection."
                            ErrorCode.AUTH -> "Authentication failed."
                            ErrorCode.INVALID_PUBKEY -> "Invalid public key — try registering a new account."
                            else -> s.message
                        }
                        Text(msg, color = MaterialTheme.colorScheme.error)
                    }
                    else -> {}
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(jwt.trim()) },
                enabled = enrollState !is EnrollUiState.Loading,
            ) {
                Text("Enroll")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = enrollState !is EnrollUiState.Loading,
            ) {
                Text("Cancel")
            }
        },
    )
}
