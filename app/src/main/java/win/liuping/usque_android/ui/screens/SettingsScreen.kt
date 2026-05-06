package win.liuping.usque_android.ui.screens

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
import win.liuping.usque_android.ui.viewmodel.SettingsViewModel

@Composable
fun SettingsScreen(vm: SettingsViewModel = viewModel()) {
    val settings by vm.settings.collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)

        OutlinedTextField(
            value = settings.sni,
            onValueChange = { vm.update(settings.copy(sni = it)) },
            label = { Text("SNI override (empty = default)") },
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
    }
}
