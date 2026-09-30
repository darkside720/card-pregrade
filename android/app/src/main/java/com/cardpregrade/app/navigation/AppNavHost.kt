package com.cardpregrade.app.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.cardpregrade.app.AppContainer
import com.cardpregrade.app.demo.DemoInspection
import com.cardpregrade.app.ui.analysis.AnalysisScreen
import com.cardpregrade.app.ui.calibration.CalibrationScreen
import com.cardpregrade.app.ui.capture.GuidedCaptureScreen
import com.cardpregrade.app.ui.home.HomeScreen
import com.cardpregrade.app.ui.results.ResultsScreen
import com.cardpregrade.app.ui.saved.SavedScansScreen
import com.cardpregrade.app.ui.scan.NewScanScreen
import com.cardpregrade.app.ui.settings.SettingsScreen

object Routes {
    const val HOME = "home"
    const val NEW_SCAN = "new_scan"
    const val CAPTURE = "capture/{sessionId}"
    const val ANALYSIS = "analysis/{sessionId}"
    const val RESULTS = "results/{resultId}"
    const val SAVED = "saved"
    const val SETTINGS = "settings"
    const val CALIBRATION = "calibration"

    const val ARG_SESSION_ID = "sessionId"
    const val ARG_RESULT_ID = "resultId"

    fun capture(sessionId: String) = "capture/$sessionId"
    fun analysis(sessionId: String) = "analysis/$sessionId"
    fun results(resultId: String) = "results/$resultId"
}

@Composable
fun AppNavHost(container: AppContainer, navController: NavHostController = rememberNavController()) {
    val back: () -> Unit = { navController.navigateUp() }

    NavHost(navController = navController, startDestination = Routes.HOME) {
        composable(Routes.HOME) {
            HomeScreen(
                container = container,
                onNewScan = { navController.navigate(Routes.NEW_SCAN) },
                onSavedScans = { navController.navigate(Routes.SAVED) },
                onDemoResult = { navController.navigate(Routes.results(DemoInspection.RESULT_ID)) },
                onSettings = { navController.navigate(Routes.SETTINGS) },
                onCalibration = { navController.navigate(Routes.CALIBRATION) },
            )
        }
        composable(Routes.NEW_SCAN) {
            NewScanScreen(
                container = container,
                onBack = back,
                onSessionCreated = { sessionId ->
                    navController.navigate(Routes.capture(sessionId)) {
                        popUpTo(Routes.NEW_SCAN) { inclusive = true }
                    }
                },
            )
        }
        composable(
            Routes.CAPTURE,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            val sessionId = requireNotNull(entry.arguments?.getString(Routes.ARG_SESSION_ID))
            GuidedCaptureScreen(
                container = container,
                sessionId = sessionId,
                onBack = back,
                onContinue = { navController.navigate(Routes.analysis(sessionId)) },
            )
        }
        composable(
            Routes.ANALYSIS,
            arguments = listOf(navArgument(Routes.ARG_SESSION_ID) { type = NavType.StringType }),
        ) { entry ->
            AnalysisScreen(
                container = container,
                sessionId = requireNotNull(entry.arguments?.getString(Routes.ARG_SESSION_ID)),
                onBack = back,
                onViewDemo = { navController.navigate(Routes.results(DemoInspection.RESULT_ID)) },
                onHome = { navController.popBackStack(Routes.HOME, inclusive = false) },
            )
        }
        composable(
            Routes.RESULTS,
            arguments = listOf(navArgument(Routes.ARG_RESULT_ID) { type = NavType.StringType }),
        ) { entry ->
            ResultsScreen(
                resultId = requireNotNull(entry.arguments?.getString(Routes.ARG_RESULT_ID)),
                onBack = back,
            )
        }
        composable(Routes.SAVED) {
            SavedScansScreen(
                container = container,
                onBack = back,
                onOpenDemo = { navController.navigate(Routes.results(DemoInspection.RESULT_ID)) },
                onOpenSession = { sessionId -> navController.navigate(Routes.capture(sessionId)) },
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(
                container = container,
                onBack = back,
                onCalibration = { navController.navigate(Routes.CALIBRATION) },
            )
        }
        composable(Routes.CALIBRATION) {
            CalibrationScreen(container = container, onBack = back)
        }
    }
}
