package com.karim.foodrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
import com.karim.foodrun.shared.orders.GroupAction
import com.karim.foodrun.shared.orders.GroupController
import com.karim.foodrun.shared.orders.GroupObserver
import com.karim.foodrun.shared.orders.GroupPage
import com.karim.foodrun.shared.orders.GroupState
import com.karim.foodrun.shared.orders.GroupText

@Composable
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
fun GroupScreen(controller: GroupController) {
    var screenState by remember(controller) { mutableStateOf(controller.state) }
    DisposableEffect(controller) {
        val observer = object : GroupObserver {
            override fun changed(state: GroupState) { screenState = state }
        }
        controller.observe(observer)
        onDispose { controller.removeObserver(observer) }
    }
    val state = screenState
    BackHandler(state.canGoBack) { controller.dispatch(GroupAction.BACK, "") }
    if (state.page == GroupPage.QUICK_SPIN) {
        Column {
            TextButton(
                onClick = { controller.dispatch(GroupAction.BACK, "") },
                modifier = Modifier.statusBarsPadding(),
            ) { Text(GroupText.backToRooms) }
            Box(Modifier.weight(1f)) { FoodRunScreen() }
        }
        return
    }
    val scroll = androidx.compose.runtime.key(state.page) { rememberLazyListState() }
    Box(
        modifier = Modifier.fillMaxSize().background(FoodColors.Cream).semantics { testTagsAsResourceId = true },
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = FoodSize.MaxContentWidth).fillMaxSize()
                .statusBarsPadding().navigationBarsPadding().imePadding(),
        ) {
            if (state.error.isNotEmpty()) GroupErrorBanner(state.error)
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("groupScreen"),
                state = scroll,
                contentPadding = PaddingValues(FoodSpacing.Page),
                verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium),
            ) {
                item(key = "header") { GroupHeader(state, controller) }
                if (state.busy) item(key = "progress") {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = FoodColors.Orange,
                    )
                }
                state.wheel?.let { wheel -> item(key = "wheel:${wheel.round.id}") { GroupWheelContent(wheel) } }
                items(state.fields, key = { "field:${it.key.name}" }) { GroupFieldContent(it, state.busy, controller) }
                items(state.buttons, key = { "action:${it.action.name}:${it.value}" }) { GroupActionButton(it, state.busy, controller) }
                items(state.cards, key = { "card:${it.id}" }) { GroupCardContent(it, state.busy, controller) }
                item(key = "footer") { Spacer(Modifier.height(FoodSpacing.Page)) }
            }
        }
    }
}

@Composable
private fun GroupHeader(state: GroupState, controller: GroupController) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
        if (state.canGoBack) TextButton(onClick = { controller.dispatch(GroupAction.BACK, "") }) {
            Text(text = GroupText.back, color = FoodColors.Orange)
        }
        Text(text = GroupText.brand, style = FoodType.Brand, color = FoodColors.Orange)
        Text(text = state.title, style = FoodType.Hero, color = FoodColors.Ink)
        Text(text = state.subtitle, style = FoodType.Input, color = FoodColors.Muted)
        if (state.status.isNotEmpty()) Text(
            text = state.status,
            style = FoodType.Status,
            color = if (state.online) FoodColors.Success else FoodColors.Muted,
        )
        if (state.roomCode.isNotEmpty()) Text(
            text = state.roomCode,
            style = FoodType.Title,
            color = FoodColors.Orange,
            modifier = Modifier.testTag("roomCode"),
        )
    }
}
