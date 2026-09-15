package com.karim.foodrun

import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.lifecycle.viewmodel.compose.viewModel
import com.karim.foodrun.shared.FoodRunEvent

/** Native presentation host. KMP destinations own every sheet transition. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FoodRunScreen(model: FoodRunViewModel = viewModel()) {
    val state = model.state
    val context = LocalContext.current
    val view = LocalView.current
    val reduceMotion = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    LaunchedEffect(model.tick) {
        if (model.tick > 0 && state.isSpinning && state.hapticsEnabled) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }
    LaunchedEffect(state.showWinner, state.historyItems.firstOrNull()?.id) {
        model.consumeWinnerFeedback()?.let {
            if (state.hapticsEnabled) view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS)
            view.announceForAccessibility(it)
        }
    }
    Box(Modifier.fillMaxSize()) {
        FoodRunContent(state, model.rotation, model.tick, reduceMotion,
            onSpin = { model.spin(reduceMotion) }, onCrew = model::openCrew, onHistory = model::openHistory)
        if (state.showWinner) state.winner?.let { WinnerDialog(it, reduceMotion, model::dismiss) }
    }
    if (state.crewPresented || state.historyPresented) {
        ModalBottomSheet(
            onDismissRequest = model::dismiss,
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = FoodColors.Cream,
            shape = RoundedCornerShape(topStart = FoodRadius.Sheet, topEnd = FoodRadius.Sheet),
        ) {
            if (state.crewPresented) CrewSheet(state, model::dispatch, reduceMotion)
            else HistorySheet(state.historyItems, model::dismiss)
        }
    }
    if (state.addPersonPresented) AddPersonDialog(state, model::updateNameDraft, model::submitName, model::dismiss)
    state.persistenceErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { model.dispatch(FoodRunEvent.DismissError) },
            text = { Text(message) },
            confirmButton = { TextButton(onClick = { model.dispatch(FoodRunEvent.DismissError) }) { Text(com.karim.foodrun.shared.FoodRunText.done) } },
        )
    }
}
