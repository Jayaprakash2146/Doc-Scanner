package com.docscanner.app.presentation.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.docscanner.app.presentation.ui.navigation.Routes
import com.docscanner.app.presentation.ui.screens.CameraScreen
import com.docscanner.app.presentation.ui.screens.CropScreen
import com.docscanner.app.presentation.ui.screens.ExportScreen
import com.docscanner.app.presentation.ui.screens.FilterScreen
import com.docscanner.app.presentation.ui.screens.SplashScreen
import com.docscanner.app.presentation.viewmodel.ScannerViewModel

@Composable
fun DocScannerApp() {
    val navController = rememberNavController()
    val viewModel: ScannerViewModel = viewModel()
    val state by viewModel.state.collectAsState()

    NavHost(navController = navController, startDestination = Routes.SPLASH) {
        composable(Routes.SPLASH) {
            SplashScreen(
                onFinished = {
                    navController.navigate(Routes.CAMERA) {
                        popUpTo(Routes.SPLASH) { inclusive = true }
                    }
                }
            )
        }
        composable(Routes.CAMERA) {
            CameraScreen(
                onCaptured = { viewModel.onPhotoCaptured(it) },
                onGalleryImported = { 
                    viewModel.onGalleryImported(it)
                    navController.navigate(Routes.CROP)
                },
                showMultiPageOption = state.showMultiPageOption,
                onContinue = { viewModel.continueScanning() },
                onStop = {
                    viewModel.stopAndProcess()
                    navController.navigate(Routes.CROP)
                }
            )
        }
        composable(Routes.CROP) {
            CropScreen(
                viewModel = viewModel,
                onApply = { navController.navigate(Routes.FILTERS) },
                onRetake = {
                    viewModel.reset()
                    navController.popBackStack(Routes.CAMERA, inclusive = false)
                }
            )
        }
        composable(Routes.FILTERS) {
            FilterScreen(
                viewModel = viewModel,
                onNext = { navController.navigate(Routes.EXPORT) },
                onBack = { navController.popBackStack() }
            )
        }
        composable(Routes.EXPORT) {
            ExportScreen(
                viewModel = viewModel,
                onNewScan = {
                    viewModel.reset()
                    navController.popBackStack(Routes.CAMERA, inclusive = false)
                }
            )
        }
    }
}
