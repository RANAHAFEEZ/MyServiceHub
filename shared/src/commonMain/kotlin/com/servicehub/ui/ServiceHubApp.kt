package com.servicehub.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.servicehub.data.AuthRepository
import com.servicehub.ui.theme.ServiceHubTheme
import io.github.jan.supabase.auth.status.SessionStatus

/** The whole app. Call this from the template's App() function. */
@Composable
fun ServiceHubApp() {
    ServiceHubTheme {
        Surface(modifier = Modifier
            .fillMaxSize()
            .statusBarsPadding()

        ) {
            val auth = remember { AuthRepository() }
            val status by auth.sessionStatus.collectAsState()
            when {
                status is SessionStatus.Authenticated -> MainScreen(auth)
                status is SessionStatus.Initializing -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("Loading...") }
                else -> AuthScreen(auth)
            }
        }
    }
}
