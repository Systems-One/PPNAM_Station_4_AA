package com.mitas.ppnam.station4aa.navigation

import android.app.Activity
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.mitas.ppnam.station4aa.PpnamApplication
import com.mitas.ppnam.station4aa.ui.home.HomeScreen
import com.mitas.ppnam.station4aa.ui.home.HomeViewModel
import com.mitas.ppnam.station4aa.ui.login.LoginScreen
import com.mitas.ppnam.station4aa.ui.login.LoginViewModel
import com.mitas.ppnam.station4aa.ui.session.SessionWatcher
import com.mitas.ppnam.station4aa.ui.settings.SettingsScreen
import com.mitas.ppnam.station4aa.ui.settings.SettingsViewModel
import com.mitas.ppnam.station4aa.ui.waste.WasteGatheringScreen
import com.mitas.ppnam.station4aa.ui.waste.WasteGatheringViewModel
import com.mitas.ppnam.station4aa.ui.weigh.WeighBagScreen
import com.mitas.ppnam.station4aa.ui.weigh.WeighBagViewModel

@Composable
fun AppNavGraph() {
    val navController = rememberNavController()
    val context = LocalContext.current
    val container = (context.applicationContext as PpnamApplication).container

    SessionWatcher(navController, container.operatorSessionHolder)

    NavHost(navController = navController, startDestination = NavRoutes.LOGIN) {
        composable(NavRoutes.LOGIN) {
            val viewModel: LoginViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        LoginViewModel(
                            authUseCase = container.authUseCase,
                            scanEventBus = container.scanEventBus,
                            connectionManager = container.connectionManager,
                            settingsRepository = container.settingsRepository,
                        )
                    }
                }
            )
            LoginScreen(
                onLoggedIn = {
                    navController.navigate(NavRoutes.HOME) {
                        popUpTo(NavRoutes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateSettings = { navController.navigate(NavRoutes.SETTINGS) },
                onExitApp = { (context as? Activity)?.finish() },
                viewModel = viewModel,
            )
        }
        composable(NavRoutes.HOME) {
            val viewModel: HomeViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        HomeViewModel(
                            connectionManager = container.connectionManager,
                            sessionHolder = container.operatorSessionHolder,
                            authUseCase = container.authUseCase,
                        )
                    }
                }
            )
            HomeScreen(
                onWasteCollection = { navController.navigate(NavRoutes.WASTE_GATHERING) },
                onWeighBag = { navController.navigate(NavRoutes.WEIGH_BAG) },
                onSettings = { navController.navigate(NavRoutes.SETTINGS) },
                viewModel = viewModel,
            )
        }
        composable(NavRoutes.WASTE_GATHERING) {
            val viewModel: WasteGatheringViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        WasteGatheringViewModel(
                            settingsRepository = container.settingsRepository,
                            connectionManager = container.connectionManager,
                            publisher = container.wasteCollectionPublisher,
                            sessionHolder = container.operatorSessionHolder,
                            authUseCase = container.authUseCase,
                            scanEventBus = container.scanEventBus,
                            catalogueRepository = container.wasteCatalogueRepository,
                            syncCatalogue = container.syncWasteCatalogueUseCase,
                            deviceId = container.deviceId,
                        )
                    }
                }
            )
            WasteGatheringScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(NavRoutes.SETTINGS) },
                viewModel = viewModel,
            )
        }
        composable(NavRoutes.WEIGH_BAG) {
            val viewModel: WeighBagViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        WeighBagViewModel(
                            connectionManager = container.connectionManager,
                            sessionHolder = container.operatorSessionHolder,
                            scanEventBus = container.scanEventBus,
                            requestCapture = container.requestWasteCaptureUseCase,
                        )
                    }
                }
            )
            WeighBagScreen(
                onBack = { navController.popBackStack() },
                onSettings = { navController.navigate(NavRoutes.SETTINGS) },
                viewModel = viewModel,
            )
        }
        composable(NavRoutes.SETTINGS) {
            val viewModel: SettingsViewModel = viewModel(
                factory = viewModelFactory {
                    initializer {
                        SettingsViewModel(
                            settingsRepository = container.settingsRepository,
                            connectionManager = container.connectionManager,
                            sessionHolder = container.operatorSessionHolder,
                            authUseCase = container.authUseCase,
                            catalogueRepository = container.wasteCatalogueRepository,
                            syncCatalogue = container.syncWasteCatalogueUseCase,
                            deviceId = container.deviceId,
                        )
                    }
                }
            )
            SettingsScreen(
                onBack = { navController.popBackStack() },
                viewModel = viewModel,
            )
        }
    }
}
