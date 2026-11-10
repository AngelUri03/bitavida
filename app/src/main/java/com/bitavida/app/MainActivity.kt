package com.bitavida.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import com.bitavida.app.ui.brand.rememberBitavidaMotionAllowed
import com.bitavida.app.ui.careprofile.CareProfileFlowRoute
import com.bitavida.app.ui.careprofile.CareProfileViewModel
import com.bitavida.app.ui.components.BitavidaFluidMotion
import com.bitavida.app.ui.demo.TodayDemoScreen
import com.bitavida.app.ui.theme.BitavidaTheme

class MainActivity : ComponentActivity() {
    private val careProfileViewModel: CareProfileViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            BitavidaTheme {
                var showCareProfileFlow by rememberSaveable { mutableStateOf(true) }
                val motionAllowed = rememberBitavidaMotionAllowed(override = null)
                val duration = if (motionAllowed) BitavidaFluidMotion.StateTransition else 0
                AnimatedContent(
                    targetState = showCareProfileFlow,
                    transitionSpec = {
                        (
                            fadeIn(tween(duration)) +
                                slideInHorizontally(tween(duration)) { it / 24 }
                            ).togetherWith(
                            fadeOut(tween(duration / 2)) +
                                slideOutHorizontally(tween(duration)) { -it / 28 },
                        )
                    },
                    label = "care_profile_to_today",
                ) { showFlow ->
                    if (showFlow) {
                        CareProfileFlowRoute(
                            viewModel = careProfileViewModel,
                            onExit = { showCareProfileFlow = false },
                        )
                    } else {
                        TodayDemoScreen()
                    }
                }
            }
        }
    }
}
