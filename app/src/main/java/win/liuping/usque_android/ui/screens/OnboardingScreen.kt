package win.liuping.usque_android.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import win.liuping.usque_android.nativebridge.ErrorCode
import win.liuping.usque_android.ui.viewmodel.OnboardingUiState
import win.liuping.usque_android.ui.viewmodel.OnboardingViewModel

private enum class RegisterMode { PERSONAL, ZERO_TRUST }

@Composable
fun OnboardingScreen(
    onSuccess: () -> Unit,
    onOpenZeroTrustAuth: (teamName: String) -> Unit = {},
    vm: OnboardingViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    var tosAccepted by remember { mutableStateOf(false) }
    var mode by remember { mutableStateOf(RegisterMode.PERSONAL) }
    var teamName by remember { mutableStateOf("") }

    LaunchedEffect(state) {
        if (state is OnboardingUiState.Success) onSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Usque", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(4.dp))
        Text(
            "Cloudflare WARP MASQUE Client",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(40.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = mode == RegisterMode.PERSONAL,
                onClick = { mode = RegisterMode.PERSONAL },
                label = { Text("Personal WARP") },
                modifier = Modifier.weight(1f),
            )
            FilterChip(
                selected = mode == RegisterMode.ZERO_TRUST,
                onClick = { mode = RegisterMode.ZERO_TRUST },
                label = { Text("ZeroTrust") },
                modifier = Modifier.weight(1f),
            )
        }

        Spacer(Modifier.height(24.dp))

        if (mode == RegisterMode.ZERO_TRUST) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text("ZeroTrust", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Enter your Cloudflare Access team name. You'll authenticate in a built-in browser and the token will be captured automatically.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        value = teamName,
                        onValueChange = { teamName = it },
                        label = { Text("Team name") },
                        placeholder = { Text("your-team") },
                        supportingText = {
                            if (teamName.isNotBlank()) {
                                Text(
                                    "https://$teamName.cloudflareaccess.com/warp",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary,
                                )
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Checkbox(checked = tosAccepted, onCheckedChange = { tosAccepted = it })
            Spacer(Modifier.width(8.dp))
            Text(
                "I accept the Cloudflare Terms of Service",
                style = MaterialTheme.typography.bodySmall,
            )
        }

        Spacer(Modifier.height(16.dp))

        when (val s = state) {
            is OnboardingUiState.Loading -> CircularProgressIndicator()
            is OnboardingUiState.Error -> {
                val msg = when (s.code) {
                    ErrorCode.TOS_NOT_ACCEPTED -> "Please accept the Terms of Service first."
                    ErrorCode.NETWORK -> "Network error. Check your connection."
                    ErrorCode.AUTH -> "Authentication failed."
                    ErrorCode.INVALID_PUBKEY -> "Invalid public key."
                    else -> s.message
                }
                Text(msg, color = MaterialTheme.colorScheme.error, textAlign = TextAlign.Center)
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        if (mode == RegisterMode.ZERO_TRUST && tosAccepted) {
                            onOpenZeroTrustAuth(teamName)
                        } else {
                            vm.register("", tosAccepted)
                        }
                    },
                    enabled = tosAccepted,
                ) { Text("Retry") }
            }
            else -> {
                if (mode == RegisterMode.PERSONAL) {
                    Button(
                        onClick = { vm.register("", tosAccepted) },
                        enabled = tosAccepted,
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Register & Continue") }
                } else {
                    Button(
                        onClick = { onOpenZeroTrustAuth(teamName) },
                        enabled = tosAccepted && teamName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth(),
                    ) { Text("Login with ZeroTrust") }
                }
            }
        }
    }
}
