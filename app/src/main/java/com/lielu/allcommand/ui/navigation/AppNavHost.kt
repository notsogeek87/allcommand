package com.lielu.allcommand.ui.navigation

import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.lielu.allcommand.AppContainer
import com.lielu.allcommand.ui.devices.AddDeviceScreen
import com.lielu.allcommand.ui.devices.AddDeviceViewModel
import com.lielu.allcommand.ui.init.InitScreen
import com.lielu.allcommand.ui.init.InitViewModel
import com.lielu.allcommand.ui.remote.RemoteScreen
import com.lielu.allcommand.ui.remote.RemoteViewModel
import com.lielu.allcommand.ui.settings.SettingsScreen
import com.lielu.allcommand.ui.settings.SettingsViewModel

object AppDestinations {
    const val INIT = "init"
    const val SETTINGS = "settings"
    const val ADD_DEVICE = "add_device"
    const val REMOTE_ARG = "deviceId"
    const val REMOTE = "remote/{$REMOTE_ARG}"

    fun remote(deviceId: String) = "remote/$deviceId"
}

@Composable
fun AppNavHost(container: AppContainer) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = AppDestinations.INIT) {
        composable(AppDestinations.INIT) { entry ->
            val viewModel: InitViewModel =
                viewModel(
                    entry,
                    factory =
                        viewModelFactory {
                            initializer {
                                InitViewModel(
                                    container.settingsRepository,
                                    container.backupManager,
                                    container.deviceRepository,
                                )
                            }
                        },
                )
            InitScreen(
                viewModel = viewModel,
                onSettingsClick = { navController.navigate(AppDestinations.SETTINGS) },
                onAddDeviceClick = { navController.navigate(AppDestinations.ADD_DEVICE) },
                onDeviceClick = { id -> navController.navigate(AppDestinations.remote(id)) },
            )
        }

        composable(AppDestinations.SETTINGS) { entry ->
            val viewModel: SettingsViewModel =
                viewModel(
                    entry,
                    factory =
                        viewModelFactory {
                            initializer {
                                SettingsViewModel(
                                    container.settingsRepository,
                                    container.backupManager,
                                    container.autoBackup,
                                    container.autoBackupScheduler,
                                    container.updateManager,
                                    container.updatesEnabled,
                                )
                            }
                        },
                )
            SettingsScreen(viewModel = viewModel, onBackClick = { navController.popBackStack() })
        }

        composable(AppDestinations.ADD_DEVICE) { entry ->
            val viewModel: AddDeviceViewModel =
                viewModel(
                    entry,
                    factory =
                        viewModelFactory {
                            initializer {
                                AddDeviceViewModel(
                                    container.deviceRepository,
                                    container.deviceDrivers,
                                    container.deviceNamer,
                                )
                            }
                        },
                )
            AddDeviceScreen(
                viewModel = viewModel,
                onBackClick = { navController.popBackStack() },
                onDone = { navController.popBackStack() },
            )
        }

        composable(
            AppDestinations.REMOTE,
            arguments = listOf(navArgument(AppDestinations.REMOTE_ARG) { type = NavType.StringType }),
        ) { entry ->
            val deviceId = entry.arguments?.getString(AppDestinations.REMOTE_ARG).orEmpty()
            val viewModel: RemoteViewModel =
                viewModel(
                    entry,
                    factory =
                        viewModelFactory {
                            initializer {
                                RemoteViewModel(deviceId, container.deviceRepository, container.deviceDrivers)
                            }
                        },
                )
            RemoteScreen(viewModel = viewModel, onBackClick = { navController.popBackStack() })
        }
    }
}
