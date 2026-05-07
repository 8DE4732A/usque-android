package win.liuping.usque_android.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import win.liuping.usque_android.data.ConfigRepository
import win.liuping.usque_android.ui.screens.HomeScreen
import win.liuping.usque_android.ui.screens.OnboardingScreen
import win.liuping.usque_android.ui.screens.SettingsScreen
import win.liuping.usque_android.ui.screens.ZeroTrustAuthScreen
import win.liuping.usque_android.ui.theme.UsqueandroidTheme
import win.liuping.usque_android.ui.viewmodel.OnboardingViewModel

object Routes {
    const val ONBOARDING = "onboarding"
    const val HOME = "home"
    const val SETTINGS = "settings"
    const val ZERO_TRUST_AUTH = "zero_trust_auth/{teamName}"

    fun zeroTrustAuth(teamName: String) = "zero_trust_auth/$teamName"
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UsqueandroidTheme {
                UsqueNavHost(hasConfig = ConfigRepository(this).hasConfig())
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UsqueNavHost(hasConfig: Boolean) {
    val navController = rememberNavController()
    val startDestination = if (hasConfig) Routes.HOME else Routes.ONBOARDING

    Scaffold(
        bottomBar = {
            val currentRoute = navController.currentBackStackEntryAsState().value?.destination?.route
            if (currentRoute == Routes.HOME || currentRoute == Routes.SETTINGS) {
                NavigationBar {
                    NavigationBarItem(
                        selected = currentRoute == Routes.HOME,
                        onClick = { navController.navigate(Routes.HOME) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
                        label = { Text("Home") },
                    )
                    NavigationBarItem(
                        selected = currentRoute == Routes.SETTINGS,
                        onClick = { navController.navigate(Routes.SETTINGS) { launchSingleTop = true } },
                        icon = { Icon(Icons.Default.Settings, contentDescription = "Settings") },
                        label = { Text("Settings") },
                    )
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = startDestination,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable(Routes.ONBOARDING) { backStackEntry ->
                // Share the OnboardingViewModel so ZeroTrustAuthScreen can call register()
                val vm: OnboardingViewModel = viewModel(backStackEntry)
                OnboardingScreen(
                    vm = vm,
                    onSuccess = {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.ONBOARDING) { inclusive = true }
                        }
                    },
                    onOpenZeroTrustAuth = { teamName ->
                        navController.navigate(Routes.zeroTrustAuth(teamName))
                    },
                )
            }
            composable(
                route = Routes.ZERO_TRUST_AUTH,
                arguments = listOf(navArgument("teamName") { type = NavType.StringType }),
            ) { backStackEntry ->
                val teamName = backStackEntry.arguments?.getString("teamName") ?: ""
                // Get the OnboardingViewModel from the Onboarding back-stack entry
                val onboardingEntry = remember(backStackEntry) {
                    navController.getBackStackEntry(Routes.ONBOARDING)
                }
                val vm: OnboardingViewModel = viewModel(onboardingEntry)
                ZeroTrustAuthScreen(
                    teamName = teamName,
                    onTokenReceived = { token ->
                        vm.register(jwt = token, acceptTos = true)
                        navController.popBackStack(Routes.ONBOARDING, inclusive = false)
                    },
                    onBack = { navController.popBackStack() },
                )
            }
            composable(Routes.HOME) {
                HomeScreen(viewModel())
            }
            composable(Routes.SETTINGS) {
                SettingsScreen(
                    viewModel(),
                    onReRegister = {
                        navController.navigate(Routes.ONBOARDING) {
                            popUpTo(0) { inclusive = true }
                        }
                    },
                )
            }
        }
    }
}
