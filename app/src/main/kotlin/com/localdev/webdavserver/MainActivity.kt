package com.localdev.webdavserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.localdev.webdavserver.data.local.model.ServerConfig
import com.localdev.webdavserver.di.AppModule
import com.localdev.webdavserver.presentation.navigation.NavGraph
import com.localdev.webdavserver.presentation.theme.AppTheme
import com.localdev.webdavserver.service.WebDavForegroundService
import kotlinx.coroutines.flow.first

/**
 * Single-activity host. Fixed AMOLED theme; a brief loading spinner is shown
 * while settings load (avoids flashing wrong UI on relaunch). Also performs the
 * "auto-start on app open" behaviour.
 */
class MainActivity : ComponentActivity() {

    private var autoStartChecked = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        AppModule.init(applicationContext)

        setContent {
            // null = settings not loaded yet.
            var config by remember { mutableStateOf<ServerConfig?>(null) }
            LaunchedEffect(Unit) {
                AppModule.settingsRepository.config.collect { config = it }
            }

            // Auto-start the server on app open (once), if enabled + configured.
            LaunchedEffect(Unit) {
                if (!autoStartChecked) {
                    autoStartChecked = true
                    val c = AppModule.settingsRepository.config.first()
                    val running = AppModule.webDavEngine.state.value.isRunning
                    if (c.autoStartOnOpen && c.setupComplete &&
                        c.rootFolderUri != null && !running
                    ) {
                        WebDavForegroundService.start(this@MainActivity)
                    }
                }
            }

            AppTheme {
                val current = config
                if (current == null) {
                    LoadingScreen()
                } else {
                    NavGraph(setupComplete = current.setupComplete)
                }
            }
        }
    }
}

@androidx.compose.runtime.Composable
private fun LoadingScreen() {
    Box(
        Modifier.fillMaxSize().background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(
            color = MaterialTheme.colorScheme.primary,
            strokeWidth = 3.dp
        )
    }
}
