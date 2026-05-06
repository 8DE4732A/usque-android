package win.liuping.usque_android.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import win.liuping.usque_android.nativebridge.ErrorCode
import win.liuping.usque_android.ui.viewmodel.OnboardingUiState
import win.liuping.usque_android.ui.viewmodel.OnboardingViewModel

@Composable
fun OnboardingScreen(
    onSuccess: () -> Unit,
    vm: OnboardingViewModel = viewModel(),
) {
    val state by vm.state.collectAsState()

    var tosAccepted by remember { mutableStateOf(false) }
    var jwt by remember { mutableStateOf("") }
    var showJwt by remember { mutableStateOf(false) }

    LaunchedEffect(state) {
        if (state is OnboardingUiState.Success) onSuccess()
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("Usque", style = MaterialTheme.typography.headlineLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Cloudflare WARP MASQUE Client",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(48.dp))

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

        Spacer(Modifier.height(8.dp))

        TextButton(onClick = { showJwt = !showJwt }) {
            Text(if (showJwt) "Hide team token" else "Have a team token?")
        }

        if (showJwt) {
            OutlinedTextField(
                value = jwt,
                onValueChange = { jwt = it },
                label = { Text("JWT / Team token (optional)") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
            )
            Spacer(Modifier.height(8.dp))
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
                Text(msg, color = MaterialTheme.colorScheme.error)
                Spacer(Modifier.height(8.dp))
                Button(onClick = { vm.register(jwt, tosAccepted) }, enabled = tosAccepted) {
                    Text("Retry")
                }
            }
            else -> {
                Button(
                    onClick = { vm.register(jwt, tosAccepted) },
                    enabled = tosAccepted,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text("Register & Continue")
                }
            }
        }
    }
}
