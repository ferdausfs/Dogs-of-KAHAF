package com.kahaf.guardianshield.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.rememberNavController
import com.kahaf.guardianshield.data.permissions.PermissionManager
import com.kahaf.guardianshield.domain.model.AppSettings
import com.kahaf.guardianshield.domain.repository.SettingsRepository
import com.kahaf.guardianshield.presentation.navigation.GuardianNavHost
import com.kahaf.guardianshield.presentation.navigation.Routes
import com.kahaf.guardianshield.presentation.theme.GuardianShieldTheme
import com.kahaf.guardianshield.service.worker.GuardianWorkScheduler
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import androidx.lifecycle.viewModelScope
import javax.inject.Inject

/**
 * Single-Activity host. Decides whether to start in Onboarding or Dashboard
 * based on cached permission status. Bootstraps WorkManager scheduling.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    @Inject lateinit var permissionManager: PermissionManager
    @Inject lateinit var workScheduler: GuardianWorkScheduler

    private var bindingReady: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindingReady = true
        workScheduler.scheduleAll()

        val initialPerm = permissionManager.refresh()
        val startDestination = if (initialPerm.allCriticalGranted) {
            Routes.DASHBOARD
        } else {
            Routes.ONBOARDING
        }

        setContent {
            GuardianShieldRoot(startDestination = startDestination)
        }
    }

    override fun onResume() {
        super.onResume()
        if (!bindingReady) return
        permissionManager.refresh()
    }
}

@HiltViewModel
class RootViewModel @Inject constructor(
    settings: SettingsRepository
) : ViewModel() {
    val appSettings: StateFlow<AppSettings> = settings.appSettings
        .stateIn(viewModelScope, SharingStarted.Eagerly, AppSettings())
}

@Composable
private fun GuardianShieldRoot(
    startDestination: String,
    vm: RootViewModel = hiltViewModel()
) {
    val settings by vm.appSettings.collectAsStateWithLifecycle()
    GuardianShieldTheme(
        themeMode = settings.themeMode,
        dynamicColor = settings.dynamicColor
    ) {
        Surface(modifier = Modifier.fillMaxSize()) {
            val navController = rememberNavController()
            GuardianNavHost(navController = navController, startDestination = startDestination)
        }
    }
}
