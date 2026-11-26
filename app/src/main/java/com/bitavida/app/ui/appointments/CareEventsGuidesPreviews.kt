package com.bitavida.app.ui.appointments

import android.content.res.Configuration
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview
import com.bitavida.app.R
import com.bitavida.app.data.education.BundledEducationRepository
import com.bitavida.app.domain.usecase.AskBitavidaAi
import com.bitavida.app.ui.components.BitavidaNavigationItem
import com.bitavida.app.ui.components.TodayAgendaNavigation
import com.bitavida.app.ui.education.BitavidaAiAction
import com.bitavida.app.ui.education.BitavidaAiViewModel
import com.bitavida.app.ui.theme.BitavidaTheme

@Preview(name = "Guías Citas claro", widthDp = 390, heightDp = 900, showBackground = true)
@Preview(
    name = "Guías Citas oscuro",
    widthDp = 390,
    heightDp = 900,
    uiMode = Configuration.UI_MODE_NIGHT_YES,
    showBackground = true,
)
@Preview(name = "Guías Citas compacto", widthDp = 320, heightDp = 720, showBackground = true)
@Preview(name = "Guías Citas texto 200%", widthDp = 320, heightDp = 900, fontScale = 2f, showBackground = true)
@Composable
private fun CareEventsGuidesHostPreview() {
    val viewModel = remember {
        BitavidaAiViewModel(AskBitavidaAi(BundledEducationRepository(loadModelText = { "" }))).apply {
            onAction(BitavidaAiAction.StartProcedureSession())
        }
    }
    val navigationItems = remember {
        listOf(
            BitavidaNavigationItem("Mi día", R.drawable.ic_v2_home),
            BitavidaNavigationItem("Plan", R.drawable.ic_v2_clipboard),
            BitavidaNavigationItem("Citas", R.drawable.ic_v2_calendar),
            BitavidaNavigationItem("Informe", R.drawable.ic_v2_chart),
        )
    }
    BitavidaTheme(darkTheme = isSystemInDarkTheme()) {
        CareEventsGuidesHost(
            viewModel = viewModel,
            initialIds = emptyList(),
            motion = false,
            bottomBar = {
                TodayAgendaNavigation(
                    items = navigationItems,
                    selectedIndex = 2,
                    onSelected = {},
                    motionEnabledOverride = false,
                )
            },
            onExit = {},
        )
    }
}
