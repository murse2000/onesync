package com.dalbear.onesync

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.dalbear.onesync.ui.AuthSetupScreen
import com.dalbear.onesync.ui.AuthViewModel
import com.dalbear.onesync.ui.DriveViewModel
import com.dalbear.onesync.ui.HomeScreen
import com.dalbear.onesync.ui.NewPairScreen
import com.dalbear.onesync.ui.PairViewModel
import com.dalbear.onesync.ui.theme.OneSyncTheme

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val driveViewModel: DriveViewModel by viewModels()
    private val pairViewModel: PairViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            OneSyncTheme {
                var showSetup by rememberSaveable {
                    mutableStateOf(authViewModel.clientId == null)
                }
                var showNewPair by rememberSaveable { mutableStateOf(false) }

                when {
                    showSetup -> AuthSetupScreen(
                        viewModel = authViewModel,
                        onBack = { showSetup = false },
                    )

                    showNewPair -> NewPairScreen(
                        viewModel = pairViewModel,
                        driveViewModel = driveViewModel,
                        onDone = { showNewPair = false },
                    )

                    else -> HomeScreen(
                        viewModel = authViewModel,
                        driveViewModel = driveViewModel,
                        pairViewModel = pairViewModel,
                        onOpenSetup = { showSetup = true },
                        onNewPair = {
                            pairViewModel.clearPending()
                            showNewPair = true
                        },
                    )
                }
            }
        }
    }
}
