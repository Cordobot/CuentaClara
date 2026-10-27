package com.cordobot.cuentaclara

import android.Manifest
import android.app.Application
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.cordobot.cuentaclara.data.FirebaseSetup
import com.cordobot.cuentaclara.data.NotificationChannels
import com.cordobot.cuentaclara.data.push.ExpenseMessagingService
import com.cordobot.cuentaclara.domain.AuthRepository
import com.cordobot.cuentaclara.domain.ConnectivityMonitor
import com.cordobot.cuentaclara.domain.model.ConnectionState
import com.cordobot.cuentaclara.domain.model.User
import com.cordobot.cuentaclara.presentation.CuentaClaraTheme
import com.cordobot.cuentaclara.presentation.OfflineBanner
import com.cordobot.cuentaclara.presentation.auth.AuthScreen
import com.cordobot.cuentaclara.presentation.group.GroupScreen
import com.cordobot.cuentaclara.presentation.groups.GroupsScreen
import com.google.firebase.messaging.FirebaseMessaging
import androidx.hilt.navigation.compose.hiltViewModel
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.HiltAndroidApp
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltAndroidApp
class CuentaClaraApp : Application() {
    override fun onCreate() {
        FirebaseSetup.init(this)
        super.onCreate()
        NotificationChannels.create(this)
    }
}

/** Which part of the app to show: still checking the session, signed out or signed in. */
sealed interface SessionState {
    data object Loading : SessionState
    data object SignedOut : SessionState
    data class SignedIn(val user: User) : SessionState
}

@HiltViewModel
class AppViewModel @Inject constructor(auth: AuthRepository, connectivity: ConnectivityMonitor) : ViewModel() {
    val session: StateFlow<SessionState> = auth.currentUser
        .map { user -> if (user == null) SessionState.SignedOut else SessionState.SignedIn(user) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, SessionState.Loading)

    val offline: StateFlow<Boolean> = connectivity.state
        .map { it == ConnectionState.Offline }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)
}

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            CuentaClaraTheme {
                Surface(color = MaterialTheme.colorScheme.background) { Root() }
            }
        }
    }
}

@Composable
private fun Root(viewModel: AppViewModel = hiltViewModel()) {
    val session by viewModel.session.collectAsStateWithLifecycle()
    val offline by viewModel.offline.collectAsStateWithLifecycle()

    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        OfflineBanner(offline)
        when (val current = session) {
            SessionState.Loading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            SessionState.SignedOut -> AuthScreen()
            is SessionState.SignedIn -> SignedInApp(current.user)
        }
    }
}

@Composable
private fun SignedInApp(user: User) {
    val navController = rememberNavController()
    val permission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(user.uid) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) permission.launch(Manifest.permission.POST_NOTIFICATIONS)
        // Push needs a real Firebase project; the emulators do not provide FCM
        if (!BuildConfig.USE_FIREBASE_EMULATOR) {
            FirebaseMessaging.getInstance().token.addOnSuccessListener(ExpenseMessagingService::saveToken)
        }
    }
    NavHost(navController, startDestination = "groups") {
        composable("groups") {
            GroupsScreen(onOpenGroup = { id -> navController.navigate("group/$id") })
        }
        composable(
            "group/{groupId}",
            enterTransition = { slideInHorizontally { it / 3 } + fadeIn() },
            popExitTransition = { slideOutHorizontally { it / 3 } + fadeOut() }
        ) {
            GroupScreen(onBack = { navController.popBackStack() })
        }
    }
}
