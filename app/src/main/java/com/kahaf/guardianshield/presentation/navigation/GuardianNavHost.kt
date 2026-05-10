package com.kahaf.guardianshield.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.kahaf.guardianshield.presentation.aisettings.AiSettingsScreen
import com.kahaf.guardianshield.presentation.applist.AppListScreen
import com.kahaf.guardianshield.presentation.dashboard.DashboardScreen
import com.kahaf.guardianshield.presentation.domains.DomainsScreen
import com.kahaf.guardianshield.presentation.keywords.KeywordsScreen
import com.kahaf.guardianshield.presentation.onboarding.OnboardingScreen
import com.kahaf.guardianshield.presentation.schedules.SchedulesScreen
import com.kahaf.guardianshield.presentation.settings.SettingsScreen

@Composable
fun GuardianNavHost(
    navController: NavHostController,
    startDestination: String
) {
    NavHost(navController = navController, startDestination = startDestination) {
        composable(Routes.ONBOARDING) {
            OnboardingScreen(onFinished = {
                navController.navigate(Routes.DASHBOARD) {
                    popUpTo(Routes.ONBOARDING) { inclusive = true }
                }
            })
        }
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                onOpenApps = { navController.navigate(Routes.APP_LIST) },
                onOpenKeywords = { navController.navigate(Routes.KEYWORDS) },
                onOpenSchedules = { navController.navigate(Routes.SCHEDULES) },
                onOpenAi = { navController.navigate(Routes.AI_SETTINGS) },
                onOpenDomains = { navController.navigate(Routes.DOMAINS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) }
            )
        }
        composable(Routes.APP_LIST) {
            AppListScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.KEYWORDS) {
            KeywordsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.DOMAINS) {
            DomainsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SCHEDULES) {
            SchedulesScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.AI_SETTINGS) {
            AiSettingsScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
