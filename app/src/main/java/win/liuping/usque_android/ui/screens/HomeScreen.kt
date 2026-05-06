package win.liuping.usque_android.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import win.liuping.usque_android.service.ServiceController
import win.liuping.usque_android.service.ServiceMode
import win.liuping.usque_android.ui.viewmodel.HomeViewModel
import win.liuping.usque_android.ui.viewmodel.TunnelStatus

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ServiceController.startVpn(context)
            vm.setConnecting()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(32.dp))

        Text("Usque", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // Status indicator
        val statusColor = when (state.status) {
            TunnelStatus.CONNECTED -> MaterialTheme.colorScheme.primary
            TunnelStatus.CONNECTING, TunnelStatus.RECONNECTING -> MaterialTheme.colorScheme.tertiary
            TunnelStatus.ERROR -> MaterialTheme.colorScheme.error
            TunnelStatus.IDLE -> MaterialTheme.colorScheme.onSurfaceVariant
        }
        val statusText = when (state.status) {
            TunnelStatus.IDLE -> "Disconnected"
            TunnelStatus.CONNECTING -> "Connecting…"
            TunnelStatus.CONNECTED -> "Connected"
            TunnelStatus.RECONNECTING -> "Reconnecting…"
            TunnelStatus.ERROR -> "Error: ${state.errorMessage}"
        }
        Text(statusText, style = MaterialTheme.typography.titleMedium, color = statusColor)

        if (state.ipv4.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text("IPv4: ${state.ipv4}", style = MaterialTheme.typography.bodySmall)
        }
        if (state.ipv6.isNotEmpty()) {
            Text("IPv6: ${state.ipv6}", style = MaterialTheme.typography.bodySmall)
        }

        Spacer(Modifier.height(32.dp))

        // Mode selector
        Text("Mode", style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            ServiceMode.entries.forEach { mode ->
                FilterChip(
                    selected = state.mode == mode,
                    onClick = { vm.setMode(mode) },
                    label = { Text(mode.name) },
                    enabled = state.status == TunnelStatus.IDLE,
                )
            }
        }

        Spacer(Modifier.height(32.dp))

        // Connect / Disconnect button
        val isIdle = state.status == TunnelStatus.IDLE
        Button(
            onClick = {
                if (isIdle) {
                    when (state.mode) {
                        ServiceMode.SOCKS5 -> {
                            ServiceController.startSocks(context)
                            vm.setConnecting()
                        }
                        ServiceMode.VPN -> {
                            val vpnIntent = VpnService.prepare(context)
                            if (vpnIntent != null) {
                                vpnPermissionLauncher.launch(vpnIntent)
                            } else {
                                ServiceController.startVpn(context)
                                vm.setConnecting()
                            }
                        }
                    }
                } else {
                    when (state.mode) {
                        ServiceMode.SOCKS5 -> ServiceController.stopSocks(context)
                        ServiceMode.VPN -> ServiceController.stopVpn(context)
                    }
                    vm.setIdle()
                }
            },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isIdle) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(if (isIdle) "Connect" else "Disconnect")
        }
    }
}
