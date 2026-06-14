package com.posturebot.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.posturebot.app.data.db.PostureDao
import com.posturebot.app.ui.screens.HistoryScreen
import com.posturebot.app.ui.screens.LiveSessionScreen
import com.posturebot.app.ui.screens.SessionDetailScreen
import com.posturebot.app.ui.screens.SessionReportScreen
import com.posturebot.app.ui.screens.WelcomeScreen
import com.posturebot.app.ui.theme.PostureBotTheme
import com.posturebot.app.viewmodel.SessionViewModel
import com.posturebot.app.viewmodel.SessionViewModelFactory
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as PostureBotApp
        val dao: PostureDao = app.database.postureDao()

        setContent {
            PostureBotTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val navController = rememberNavController()
                    val viewModel: SessionViewModel = viewModel(
                        factory = SessionViewModelFactory(application as PostureBotApp, dao)
                    )
                    val scope = rememberCoroutineScope()

                    val navBackStackEntry by navController.currentBackStackEntryAsState()
                    val currentRoute = navBackStackEntry?.destination?.route ?: "welcome"

                    // Only clean up session when the activity is disposed
                    DisposableEffect(Unit) {
                        onDispose { viewModel.endSession() }
                    }

                    Scaffold(
                        bottomBar = {
                            // Hide bottom bar on the welcome, report, and detail screens
                            if (currentRoute != "welcome" &&
                                currentRoute != "report" &&
                                !currentRoute.startsWith("session_detail")) {
                                NavigationBar {
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.Favorite, contentDescription = "Live") },
                                        label = { Text("Live") },
                                        selected = currentRoute == "live",
                                        onClick = {
                                            if (currentRoute != "live") {
                                                navController.navigate("live") {
                                                    popUpTo("live") { inclusive = true }
                                                }
                                            }
                                        }
                                    )
                                    NavigationBarItem(
                                        icon = { Icon(Icons.Default.List, contentDescription = "History") },
                                        label = { Text("History") },
                                        selected = currentRoute == "history",
                                        onClick = {
                                            if (currentRoute != "history") {
                                                navController.navigate("history") {
                                                    popUpTo("live")
                                                }
                                            }
                                        }
                                    )
                                }
                            }
                        }
                    ) { innerPadding ->
                        NavHost(
                            navController = navController,
                            startDestination = "welcome",
                            modifier = Modifier.padding(innerPadding)
                        ) {
                            composable("welcome") {
                                WelcomeScreen(
                                    onConnect = { serverUrl ->
                                        viewModel.connectToBackend(serverUrl)
                                        navController.navigate("live") {
                                            popUpTo("welcome") { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("live") {
                                val state by viewModel.stateFlow.collectAsState()
                                val metrics by viewModel.metricsText.collectAsState()
                                val issues by viewModel.issuesFlow.collectAsState()
                                val calibrationProgress by viewModel.calibrationProgress.collectAsState()
                                val postureStateHistory by viewModel.postureStateHistory.collectAsState()
                                val connectionState by viewModel.connectionState.collectAsState()
                                val bodyPartPercentages by viewModel.bodyPartPercentages.collectAsState()

                                LiveSessionScreen(
                                    state = state,
                                    metrics = metrics,
                                    issues = issues,
                                    bodyPartPercentages = bodyPartPercentages,
                                    calibrationProgress = calibrationProgress,
                                    postureStateHistory = postureStateHistory,
                                    connectionState = connectionState,
                                    onFinishCalibration = {
                                        // Calibration auto-completes from Python backend
                                        // This button is a UX confirmation
                                    },
                                    onStopAnalyzing = {
                                        viewModel.stopAndShowReport()
                                        navController.navigate("report") {
                                            popUpTo("live") { inclusive = true }
                                        }
                                    },
                                    onStartCalibration = {
                                        viewModel.requestCalibration()
                                    },
                                    onReconnect = {
                                        viewModel.reconnect()
                                    },
                                    onGoBackToCalibration = {
                                        viewModel.goBackToCalibration()
                                        navController.navigate("welcome") {
                                            popUpTo(0) { inclusive = true }
                                        }
                                    }
                                )
                            }
                            composable("report") {
                                val report by viewModel.sessionReport.collectAsState()

                                report?.let { sessionReport ->
                                    SessionReportScreen(
                                        report = sessionReport,
                                        onReturnToHome = {
                                            viewModel.goBackToCalibration()
                                            navController.navigate("welcome") {
                                                popUpTo(0) { inclusive = true }
                                            }
                                        }
                                    )
                                }
                            }
                            composable("history") {
                                val sessions by dao.getAllSessions()
                                    .collectAsState(initial = emptyList())
                                HistoryScreen(
                                    sessions = sessions,
                                    onSessionClick = { session ->
                                        navController.navigate("session_detail/${session.sessionId}")
                                    }
                                )
                            }
                            composable("session_detail/{sessionId}") { backStackEntry ->
                                val sessionId = backStackEntry.arguments?.getString("sessionId") ?: return@composable
                                val sessions by dao.getAllSessions()
                                    .collectAsState(initial = emptyList())
                                val session = sessions.find { it.sessionId == sessionId }
                                if (session != null) {
                                    SessionDetailScreen(
                                        session = session,
                                        dao = dao,
                                        onBack = { navController.popBackStack() }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
