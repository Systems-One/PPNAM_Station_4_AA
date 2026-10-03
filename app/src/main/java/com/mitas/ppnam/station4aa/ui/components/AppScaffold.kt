package com.mitas.ppnam.station4aa.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mitas.ppnam.station4aa.ui.theme.*
import com.mitas.ppnam.station4aa.ui.theme.brandTextButtonColors

/**
 * Shared top-bar chrome, mirroring Station 2's AppScaffold. With operatorName null the bar
 * collapses to a plain TopAppBar with the title, optional Settings icon and the connection pill.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppScaffold(
    title: String,
    status: ConnectionStatus,
    onBack: (() -> Unit)? = null,
    onSettings: (() -> Unit)? = null,
    operatorName: String? = null,
    operatorRole: String? = null,
    onLogout: (() -> Unit)? = null,
    loading: Boolean = false,
    content: @Composable (PaddingValues) -> Unit
) {
    val (dotColor, statusLabel) = when (status) {
        ConnectionStatus.Connected      -> SuccessGreen to "Connected"
        ConnectionStatus.Reconnecting   -> WarningOrange to "Reconnecting"
        // Broker reachable, Station 4 itself is not: publishes still leave the device and queue
        // in the outbox, so this is a warning rather than the red "nothing works" state.
        ConnectionStatus.StationOffline -> WarningOrange to "Station 4 offline"
        ConnectionStatus.Offline        -> DangerRed to "Offline"
    }

    var showLogoutDialog by remember { mutableStateOf(false) }

    if (showLogoutDialog) {
        AlertDialog(
            onDismissRequest = { showLogoutDialog = false },
            title = { Text("Log out?", color = TextPrimary) },
            text = { Text("You'll need to log in again to continue.", color = TextMuted) },
            confirmButton = {
                TextButton(onClick = {
                    showLogoutDialog = false
                    onLogout?.invoke()
                }) { Text("Log out", color = DangerRed) }
            },
            dismissButton = {
                TextButton(onClick = { showLogoutDialog = false }, colors = brandTextButtonColors()) { Text("Cancel") }
            },
            containerColor = GraphiteSurface
        )
    }

    val statusPill: @Composable () -> Unit = {
        Box(
            modifier = Modifier
                .padding(end = 12.dp)
                .clip(RoundedCornerShape(50))
                .background(dotColor.copy(alpha = 0.12f))
                .padding(horizontal = 10.dp, vertical = 5.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Canvas(modifier = Modifier.size(6.dp)) {
                    drawCircle(color = dotColor)
                }
                Spacer(Modifier.width(5.dp))
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = dotColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }

    Scaffold(
        topBar = {
            // statusBarsPadding(), applied INSIDE background(), is what stops the top bar
            // drawing under the system status bar: MainActivity calls enableEdgeToEdge(), so
            // without this the title overlapped the clock. The surface colour still paints the
            // whole node — status-bar strip included — so the bar reads as one solid block
            // rather than a floating row with a transparent gap above it.
            Column(
                modifier = Modifier
                    .background(GraphiteSurface)
                    .statusBarsPadding()
            ) {
                if (operatorName != null) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(64.dp)
                            .padding(horizontal = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (onBack != null) {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.focusProperties { canFocus = false },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back",
                                    tint = BrandTint
                                )
                            }
                        }
                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                            TextButton(
                                onClick = { showLogoutDialog = true },
                                modifier = Modifier.focusProperties { canFocus = false },
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Logout,
                                    contentDescription = "Log out",
                                    tint = BrandTint,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    // With a back arrow in the row there is no room for the role
                                    // ("Operator One · Op…" on every sub-screen, audit S4-11).
                                    text = if (onBack == null && !operatorRole.isNullOrBlank()) "$operatorName · $operatorRole" else operatorName,
                                    color = TextPrimary,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                        if (onSettings != null) {
                            IconButton(
                                onClick = onSettings,
                                modifier = Modifier.focusProperties { canFocus = false },
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Settings,
                                    contentDescription = "Settings",
                                    tint = TextMuted
                                )
                            }
                        }
                        statusPill()
                    }
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelLarge,
                        color = TextPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 16.dp, bottom = 10.dp)
                    )
                } else {
                    // No operator to anchor a second row on - a single standard bar with the
                    // title in its usual slot has plenty of room and needs no extra row.
                    TopAppBar(
                        title = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                color = TextPrimary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        navigationIcon = {
                            if (onBack != null) {
                                IconButton(
                                onClick = onBack,
                                modifier = Modifier.focusProperties { canFocus = false },
                            ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back",
                                        tint = BrandTint
                                    )
                                }
                            }
                        },
                        actions = {
                            if (onSettings != null) {
                                IconButton(
                                onClick = onSettings,
                                modifier = Modifier.focusProperties { canFocus = false },
                            ) {
                                    Icon(
                                        imageVector = Icons.Filled.Settings,
                                        contentDescription = "Settings",
                                        tint = TextMuted
                                    )
                                }
                            }
                            statusPill()
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = GraphiteSurface
                        )
                    )
                }

                // The one loading affordance in the app. A centred spinner blanks the page it
                // sits on, so whatever the operator was reading disappears for the duration and
                // reappears somewhere else; a bar under the title leaves the content in place and
                // still reads at a glance from arm's length on the handheld.
                // Always reserve the bar's 4 dp so content does not jump when a request starts.
                Box(Modifier.fillMaxWidth().height(4.dp)) {
                    if (loading) {
                        LinearProgressIndicator(
                            modifier = Modifier.fillMaxSize(),
                            color = BrandTint,
                            trackColor = GraphiteBorder
                        )
                    }
                }
            }
        },
        containerColor = GraphiteBackground,
        // safeDrawing, not the default systemBars: it also covers the display cutout and — the
        // reason it matters here — the IME. Every screen applies this PaddingValues, so when the
        // keyboard opens the content area shrinks above it instead of the window panning and
        // shoving the top bar off-screen entirely. Insets already consumed by the top bar above
        // are not double-counted.
        contentWindowInsets = WindowInsets.safeDrawing,
        content = content
    )
}
