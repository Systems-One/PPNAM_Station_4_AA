package com.mitas.ppnam.station4aa.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitas.ppnam.station4aa.R
import com.mitas.ppnam.station4aa.ui.theme.BrandTint
import com.mitas.ppnam.station4aa.ui.theme.GraphiteBorder
import com.mitas.ppnam.station4aa.ui.theme.GraphiteSurface
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary

/**
 * The suite's Home header card (suite consistency C2), the Compose twin of Station 5's
 * `activity_main.xml` `cardHeader`: a rounded 18 dp card with a 1 dp border holding the Systems One
 * logo, the Settings gear and the connection pill, with the operator row ("name · role" behind a
 * logout icon) underneath.
 *
 * The operator row has the card's full width to itself, so a long operator name is not squeezed by
 * the pill (the reason the earlier critique moved the pill off the operator row, P2-5). The logo is
 * capped at 180 × 48 dp and shrinks before the gear and a wide pill ("Station 4 offline") can
 * collide with it.
 *
 * Home only: inner screens keep [AppScaffold]'s back-arrow top bar.
 */
@Composable
fun HomeHeaderCard(
    status: ConnectionStatus,
    operatorName: String?,
    operatorRole: String?,
    onSettings: () -> Unit,
    onLogout: (() -> Unit)?,
    modifier: Modifier = Modifier,
) {
    var showLogoutDialog by remember { mutableStateOf(false) }
    if (showLogoutDialog) {
        LogoutConfirmDialog(
            onDismiss = { showLogoutDialog = false },
            onConfirm = {
                showLogoutDialog = false
                onLogout?.invoke()
            },
        )
    }

    Card(
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = GraphiteSurface),
        border = BorderStroke(1.dp, GraphiteBorder),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
        modifier = modifier.fillMaxWidth(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // The Box takes whatever the gear and pill leave; the logo fills it up to 180 dp.
                Box(Modifier.weight(1f).padding(end = 8.dp)) {
                    Image(
                        painter = painterResource(R.drawable.s1_logo_white),
                        contentDescription = "Systems One",
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.widthIn(max = 180.dp).fillMaxWidth().height(48.dp),
                    )
                }
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier.focusProperties { canFocus = false },
                ) {
                    Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TextPrimary)
                }
                ConnectionPill(status)
            }

            if (operatorName != null) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .padding(top = 8.dp)
                        .clip(RoundedCornerShape(8.dp))
                        // Before clickable, so it reaches clickable's focus target: a focusable
                        // row took initial focus and drew a grey focus highlight behind the name.
                        .focusProperties { canFocus = false }
                        // A Home without an onLogout must not offer a Log out whose confirm
                        // silently does nothing (visual critique 2026-11-06 P1-2).
                        .clickable(enabled = onLogout != null) { showLogoutDialog = true }
                        .padding(4.dp),
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Log out",
                        tint = BrandTint,
                        modifier = Modifier.size(16.dp),
                    )
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (!operatorRole.isNullOrBlank()) "$operatorName · $operatorRole" else operatorName,
                        color = TextPrimary,
                        style = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}
