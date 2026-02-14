package com.example.pitwise.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.pitwise.data.local.OnboardingPreferences
import com.example.pitwise.ui.screen.login.LoginScreen
import com.example.pitwise.ui.screen.splash.SplashScreen
import com.example.pitwise.ui.screen.welcome.WelcomeScreen

@Composable
fun PitwiseNavGraph(
    navController: NavHostController,
    onboardingPreferences: OnboardingPreferences
) {
    NavHost(
        navController = navController,
        startDestination = Screen.Splash.route
    ) {
        composable(Screen.Splash.route) {
            SplashScreen(
                onNavigateToWelcome = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(Screen.Splash.route) { inclusive = true }
                    }
                }
            )
        }

        composable(Screen.Welcome.route) {
            WelcomeScreen(
                onEnterAsGuest = {
                    navController.navigate(Screen.MainShell.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onSyncLogin = {
                    navController.navigate(Screen.Login.route)
                }
            )
        }

        composable(Screen.Login.route) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Screen.MainShell.route) {
                        popUpTo(Screen.Welcome.route) { inclusive = true }
                    }
                },
                onBack = {
                    navController.popBackStack()
                }
            )
        }



        composable(Screen.MainShell.route) {
            MainScaffold(
                parentNavController = navController,
                onLogout = {
                    navController.navigate(Screen.Welcome.route) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onboardingPreferences = onboardingPreferences
            )
        }
    }
}
