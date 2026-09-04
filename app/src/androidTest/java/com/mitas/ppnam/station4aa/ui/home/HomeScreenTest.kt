package com.mitas.ppnam.station4aa.ui.home

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.mitas.ppnam.station4aa.R
import com.mitas.ppnam.station4aa.ui.theme.AmberPrimary
import com.mitas.ppnam.station4aa.ui.theme.CyanAccent
import com.mitas.ppnam.station4aa.ui.theme.PPNAMStation4AATheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Renders the real dashboard tiles on a real device, vector drawables and all.
 *
 * This exists because the screen is otherwise unreachable on the bench: Station 4's backend has to
 * be up before anyone can sign in and get past the login screen, so a tile that failed to compose
 * — a bad vector path, a missing resource — would ship unnoticed until the day someone tried it in
 * the plant.
 */
@RunWith(AndroidJUnit4::class)
class HomeScreenTest {

    @get:Rule
    val compose = createComposeRule()

    private fun tiles(
        onWasteCollection: () -> Unit = {},
        onWeighBag: () -> Unit = {},
    ) {
        compose.setContent {
            PPNAMStation4AATheme {
                // The weights mirror HomeScreen's own Row: without them each 200dp card takes its
                // intrinsic width and the second lands off-screen, where it cannot be tapped.
                Row(modifier = Modifier.fillMaxWidth()) {
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

    @Test
    fun bothTilesRenderWithTheirArtworkAndAreTappable() {
        tiles()

        compose.onNodeWithText("Waste\nCollection").assertHasClickAction()
        compose.onNodeWithText("Weigh\nBag").assertHasClickAction()
    }

    @Test
    fun eachTileOpensItsOwnWorkflow() {
        val opened = mutableListOf<String>()
        tiles(
            onWasteCollection = { opened += "collection" },
            onWeighBag = { opened += "weigh" },
        )

        compose.onNodeWithText("Waste\nCollection").performClick()
        compose.onNodeWithText("Weigh\nBag").performClick()

        assertEquals(listOf("collection", "weigh"), opened)
    }

    @Test
    fun theTwoTilesUseStation1sDashboardColours() {
        // Station 1 AA's activity_main.xml paints its two tiles tile_blue (#2E77F5) and tile_teal
        // (#25C7DA). This app's palette already carries both, and the dashboard is only "like
        // Station 1's" for as long as they stay in step.
        assertEquals(0xFF2E77F5.toInt(), AmberPrimary.toArgb())
        assertEquals(0xFF25C7DA.toInt(), CyanAccent.toArgb())
    }
}
