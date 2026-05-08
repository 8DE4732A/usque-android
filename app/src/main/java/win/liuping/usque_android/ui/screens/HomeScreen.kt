package win.liuping.usque_android.ui.screens

import android.app.Activity
import android.net.VpnService
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import win.liuping.usque_android.data.IpGeoInfo
import win.liuping.usque_android.service.ServiceController
import win.liuping.usque_android.ui.viewmodel.HomeViewModel
import win.liuping.usque_android.ui.viewmodel.TunnelStatus

private val LocalGreen = Color(0xFF4CAF50)
private val ExitOrange = Color(0xFFFF7043)
private val LineBlue = Color(0xFF42A5F5)

@Composable
fun HomeScreen(vm: HomeViewModel = viewModel()) {
    val state by vm.state.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val vpnPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            ServiceController.startTun2Socks(context)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(48.dp))

        Text("Usque", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        Spacer(Modifier.height(8.dp))

        // Status indicator
        val statusColor = when (state.status) {
            TunnelStatus.CONNECTED -> LocalGreen
            TunnelStatus.CONNECTING, TunnelStatus.RECONNECTING -> LineBlue
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center) {
            Box(
                Modifier
                    .size(8.dp)
                    .background(statusColor, CircleShape)
            )
            Spacer(Modifier.width(6.dp))
            Text(statusText, style = MaterialTheme.typography.titleMedium, color = statusColor)
        }

        Spacer(Modifier.height(40.dp))

        // Connect / Disconnect button
        val isIdle = state.status == TunnelStatus.IDLE

        Button(
            onClick = {
                if (!isIdle) {
                    ServiceController.stopTun2Socks(context)
                    vm.setIdle()
                } else {
                    scope.launch {
                        // If local geo not ready yet, wait for it first
                        if (!state.readyToConnect) vm.fetchLocalGeoAndReady()
                        val vpnIntent = VpnService.prepare(context)
                        if (vpnIntent != null) {
                            vpnPermissionLauncher.launch(vpnIntent)
                        } else {
                            ServiceController.startTun2Socks(context)
                        }
                    }
                }
            },
            enabled = true,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = if (isIdle) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.error,
            ),
        ) {
            Text(
                if (isIdle) "Connect" else "Disconnect",
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }

        Spacer(Modifier.height(32.dp))

        ConnectionCard(
            localGeo = state.localGeo,
            proxyGeo = state.proxyGeo,
            localLoading = state.localGeoLoading,
            proxyLoading = state.proxyGeoLoading,
            connected = state.status == TunnelStatus.CONNECTED,
        )
    }
}

@Composable
private fun ConnectionCard(
    localGeo: IpGeoInfo?,
    proxyGeo: IpGeoInfo?,
    localLoading: Boolean,
    proxyLoading: Boolean,
    connected: Boolean,
) {
    val dashAnim = rememberInfiniteTransition(label = "dash")
    val dashOffset by dashAnim.animateFloat(
        initialValue = 0f,
        targetValue = 40f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "dashOffset",
    )

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        tonalElevation = 3.dp,
    ) {
        Column(modifier = Modifier.padding(20.dp)) {
            Text(
                "Connection Route",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Spacer(Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Local IP node
                IpNode(
                    modifier = Modifier.weight(1f),
                    label = "Your IP",
                    geo = localGeo,
                    dotColor = LocalGreen,
                    align = Alignment.Start,
                    isLoading = localLoading,
                )

                // Middle: line (shown when connected) or spacer
                AnimatedVisibility(
                    visible = connected,
                    modifier = Modifier.weight(1.2f),
                    enter = fadeIn(),
                    exit = fadeOut(),
                ) {
                    Box(
                        modifier = Modifier
                            .height(36.dp)
                            .drawBehind {
                                val cy = size.height / 2f
                                val pathEffect = PathEffect.dashPathEffect(
                                    floatArrayOf(12f, 8f), phase = dashOffset,
                                )
                                drawLine(
                                    brush = Brush.horizontalGradient(
                                        listOf(LocalGreen, LineBlue, ExitOrange)
                                    ),
                                    start = Offset(8f, cy),
                                    end = Offset(size.width - 8f, cy),
                                    strokeWidth = 2.5f,
                                    cap = StrokeCap.Round,
                                    pathEffect = pathEffect,
                                )
                                val ax = size.width - 8f
                                drawLine(ExitOrange, Offset(ax - 10f, cy - 6f), Offset(ax, cy), 2.5f, cap = StrokeCap.Round)
                                drawLine(ExitOrange, Offset(ax - 10f, cy + 6f), Offset(ax, cy), 2.5f, cap = StrokeCap.Round)
                            }
                    )
                }

                // Exit IP node — shown when connected
                AnimatedVisibility(
                    visible = connected,
                    modifier = Modifier.weight(1f),
                    enter = fadeIn() + expandHorizontally(expandFrom = Alignment.Start),
                    exit = fadeOut() + shrinkHorizontally(shrinkTowards = Alignment.Start),
                ) {
                    IpNode(
                        modifier = Modifier.fillMaxWidth(),
                        label = "Exit IP",
                        geo = proxyGeo,
                        dotColor = ExitOrange,
                        align = Alignment.End,
                        isLoading = proxyLoading,
                    )
                }
            }
        }
    }
}

@Composable
private fun IpNode(
    modifier: Modifier = Modifier,
    label: String,
    geo: IpGeoInfo?,
    dotColor: Color,
    align: Alignment.Horizontal,
    isLoading: Boolean = false,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = align,
    ) {
        // Dot + label row
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = if (align == Alignment.Start)
                Arrangement.Start else Arrangement.End,
        ) {
            if (align == Alignment.Start) {
                Box(Modifier.size(10.dp).background(dotColor, CircleShape))
                Spacer(Modifier.width(5.dp))
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                Text(label, style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(5.dp))
                Box(Modifier.size(10.dp).background(dotColor, CircleShape))
            }
        }

        Spacer(Modifier.height(6.dp))

        if (isLoading) {
            CircularProgressIndicator(
                modifier = Modifier.size(16.dp).run {
                    if (align == Alignment.End) padding(end = 0.dp) else this
                },
                strokeWidth = 1.5.dp,
                color = dotColor,
            )
        } else if (geo != null) {
            Text(
                geo.ip,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            val location = buildString {
                if (geo.city.isNotEmpty()) append(geo.city)
                if (geo.city.isNotEmpty() && geo.country.isNotEmpty()) append(", ")
                append(geo.country)
            }
            if (location.isNotEmpty()) {
                Text(
                    location,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                "—",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
