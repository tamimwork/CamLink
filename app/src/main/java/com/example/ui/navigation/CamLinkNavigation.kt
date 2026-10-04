package com.example.ui.navigation

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.ui.screens.connectiontest.ConnectionTestScreen
import com.example.ui.screens.connectiontest.ConnectionTestViewModel
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.home.HomeViewModel
import com.example.ui.screens.onboarding.OnboardingScreen
import com.example.ui.screens.scanner.ScannerScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.screens.settings.SettingsViewModel
import com.example.ui.screens.streaming.StreamingScreen
import com.example.ui.screens.streaming.StreamingViewModel

object CamLinkRoutes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SCANNER = "scanner"
    const val STREAMING = "streaming"
    const val CONNECTION_TEST = "connection_test"
    const val SETTINGS = "settings"
}

@Composable
fun CamLinkNavHost(
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController()
) {
    val context = LocalContext.current
    val hasCamera = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
    val hasMic = ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    val startDestination = if (hasCamera && hasMic) {
        CamLinkRoutes.HOME
    } else {
        CamLinkRoutes.ONBOARDING
    }

    NavHost(
        navController = navController,
        startDestination = startDestination,
        modifier = modifier,
        enterTransition = { fadeIn(animationSpec = tween(250)) },
        exitTransition = { fadeOut(animationSpec = tween(250)) }
    ) {
        composable(CamLinkRoutes.ONBOARDING) {
            OnboardingScreen(
                onPermissionsGranted = {
                    navController.navigate(CamLinkRoutes.HOME) {
                        popUpTo(CamLinkRoutes.ONBOARDING) { inclusive = true }
                    }
                }
            )
        }

        composable(CamLinkRoutes.HOME) {
            val homeViewModel: HomeViewModel = viewModel()
            HomeScreen(
                viewModel = homeViewModel,
                onNavigateToScanner = {
                    navController.navigate(CamLinkRoutes.SCANNER)
                },
                onNavigateToStreaming = {
                    navController.navigate(CamLinkRoutes.STREAMING)
                },
                onNavigateToConnectionTest = {
                    navController.navigate(CamLinkRoutes.CONNECTION_TEST)
                },
                onNavigateToSettings = {
                    navController.navigate(CamLinkRoutes.SETTINGS)
                }
            )
        }

        composable(
            route = CamLinkRoutes.SCANNER,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Up,
                    animationSpec = tween(300)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Down,
                    animationSpec = tween(300)
                )
            }
        ) {
            val homeViewModel: HomeViewModel = viewModel()
            ScannerScreen(
                onConfigScanned = { config ->
                    homeViewModel.startSession(config)
                    navController.navigate(CamLinkRoutes.STREAMING) {
                        popUpTo(CamLinkRoutes.SCANNER) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = CamLinkRoutes.STREAMING,
            enterTransition = { fadeIn(animationSpec = tween(350)) },
            exitTransition = { fadeOut(animationSpec = tween(350)) }
        ) {
            val streamingViewModel: StreamingViewModel = viewModel()
            StreamingScreen(
                viewModel = streamingViewModel,
                onNavigateBack = {
                    navController.navigate(CamLinkRoutes.HOME) {
                        popUpTo(CamLinkRoutes.STREAMING) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = CamLinkRoutes.CONNECTION_TEST,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(280)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(280)
                )
            }
        ) {
            val homeViewModel: HomeViewModel = viewModel()
            val connectionTestViewModel: ConnectionTestViewModel = viewModel()
            ConnectionTestScreen(
                viewModel = connectionTestViewModel,
                onStartStreaming = { config ->
                    homeViewModel.startSession(config)
                    navController.navigate(CamLinkRoutes.STREAMING) {
                        popUpTo(CamLinkRoutes.CONNECTION_TEST) { inclusive = true }
                    }
                },
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }

        composable(
            route = CamLinkRoutes.SETTINGS,
            enterTransition = {
                slideIntoContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.Start,
                    animationSpec = tween(280)
                )
            },
            exitTransition = {
                slideOutOfContainer(
                    towards = AnimatedContentTransitionScope.SlideDirection.End,
                    animationSpec = tween(280)
                )
            }
        ) {
            val settingsViewModel: SettingsViewModel = viewModel()
            SettingsScreen(
                viewModel = settingsViewModel,
                onNavigateBack = {
                    navController.popBackStack()
                }
            )
        }
    }
}
