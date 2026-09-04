package com.mitas.ppnam.station4aa.ui.home

import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mitas.ppnam.station4aa.R
import com.mitas.ppnam.station4aa.ui.components.AppScaffold
import com.mitas.ppnam.station4aa.ui.theme.AmberPrimary
import com.mitas.ppnam.station4aa.ui.theme.CyanAccent
import com.mitas.ppnam.station4aa.ui.theme.TextPrimary

/**
 * The operator's dashboard: one tile per sub-app, mirroring Station 1 AA's `activity_main.xml`
 * grid so a handheld operator moving between stations meets the same shape — a two-column grid of
 * tall coloured cards, each a centred icon above a bold label.
 *
 * The two workflows are deliberately separate doors rather than one long screen. They happen at
 * different times and places: a collection is registered wherever the bag is filled, and the weigh
 * happens later at Station 4's scale, keyed only by the bag code. Neither is a step of the other.
 *
 * The tile colours are Station 1's `tile_blue` and `tile_teal`, which this app's palette already
 * carries as [AmberPrimary] and [CyanAccent].
 */
@Composable
fun HomeScreen(
    onWasteCollection: () -> Unit,
    onWeighBag: () -> Unit,
    onSettings: () -> Unit,
    viewModel: HomeViewModel,
) {
    val connectionStatus by viewModel.connectionStatus.collectAsState()
    val session by viewModel.session.collectAsState()

    AppScaffold(
        title = "Station 4",
        status = connectionStatus,
        onSettings = onSettings,
        operatorName = session?.operatorName?.ifBlank { session?.operatorId },
        operatorRole = session?.role,
        onLogout = viewModel::logout,
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                DashboardTile(
                    label = "Waste\nCollection",
                    icon = R.drawable.ic_waste_collection,
                    containerColor = AmberPrimary,
                    onClick = onWasteCollection,
                    modifier = Modifier.weight(1f),
                )
                DashboardTile(
                    label = "Weigh\nBag",
                    icon = R.drawable.ic_weigh_bag,
                    containerColor = CyanAccent,
                    onClick = onWeighBag,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * One dashboard tile: Station 1's 200dp card, 80dp centred icon, bold 18sp label beneath.
 *
 * Internal rather than private so `HomeScreenTest` can render the real thing — including actually
 * decoding the tile's vector drawable. Station 4's backend must be up before anyone can sign in
 * and reach this screen by hand, so a test that settled for a stand-in would leave the real tile
 * unexercised until a day the station happened to be running.
 */
@Composable
internal fun DashboardTile(
    label: String,
    @DrawableRes icon: Int,
    containerColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        onClick = onClick,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        shape = MaterialTheme.shapes.large,
        modifier = modifier.height(200.dp),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = painterResource(icon),
                contentDescription = null, // the label below already names the tile
                modifier = Modifier.size(80.dp),
            )
            Text(
                label,
                style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
                color = TextPrimary,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
