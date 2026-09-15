package com.karim.foodrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
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
import androidx.compose.runtime.LaunchedEffect
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
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text(GroupText.backToRooms, modifier = Modifier.padding(start = FoodSpacing.XSmall))
            }
            Box(Modifier.weight(1f)) { FoodRunScreen() }
        }
        return
    }
    var optionsExpanded by remember(state.page) { mutableStateOf(false) }
    LaunchedEffect(state.error) {
        if (state.error.isNotEmpty() && state.extraFields.isNotEmpty()) optionsExpanded = true
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
            if (state.busy) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = FoodColors.Orange)
                Text(GroupText.working, style = FoodType.Status, color = FoodColors.Muted, modifier = Modifier.padding(FoodSpacing.XSmall))
            }
            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f).testTag("groupScreen"),
                state = scroll,
                contentPadding = PaddingValues(FoodSpacing.Page),
                verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium),
            ) {
                if (state.page == GroupPage.HOME) {
                    item(key = "home") { GroupHomeContent(state, controller) }
                } else {
                    item(key = "header") { GroupHeader(state, controller) }
                    if (state.progressStep >= 0) item(key = "steps") { GroupProgress(state.progressStep) }
                    state.wheel?.let { wheel -> item(key = "wheel:${wheel.round.id}") { GroupWheelContent(wheel) } }
                    items(state.mainFields, key = { "field:${it.key.name}" }) { GroupFieldContent(it, state.busy, controller) }
                    if (state.page != GroupPage.ROOM && state.extraFields.isNotEmpty()) item(key = "extras") {
                        GroupOptions(state, controller, optionsExpanded) { optionsExpanded = !optionsExpanded }
                    }
                    items(state.inlineButtons, key = { "action:${it.action.name}:${it.value}" }) { GroupActionButton(it, state.busy, controller, prominent = false) }
                    state.sections.forEachIndexed { index, section ->
                        if (section.title.isNotEmpty()) item(key = "section:$index") { GroupSectionHeading(section.title, section.cards.size) }
                        items(section.cards, key = { "card:${it.id}" }) { GroupCardContent(it, state.busy, controller) }
                    }
                    if (state.page == GroupPage.ROOM && (state.extraFields.isNotEmpty() || state.utilityButtons.isNotEmpty())) item(key = "extras") {
                        GroupOptions(state, controller, optionsExpanded) { optionsExpanded = !optionsExpanded }
                    }
                }
                item(key = "footer") { Spacer(Modifier.height(FoodSpacing.XSmall)) }
            }
            if (state.page != GroupPage.HOME) state.primaryAction?.let { primary ->
                FoodDivider()
                Box(Modifier.padding(FoodSpacing.Large)) { GroupActionButton(primary, state.busy, controller) }
            }
        }
    }
}

@Composable
private fun GroupHeader(state: GroupState, controller: GroupController) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.canGoBack) TextButton(onClick = { controller.dispatch(GroupAction.BACK, "") }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(FoodSize.IconSmall))
                Text(text = GroupText.back, modifier = Modifier.padding(start = FoodSpacing.XSmall), color = FoodColors.Orange)
            }
            Spacer(Modifier.weight(1f))
            Text(com.karim.foodrun.shared.FoodRunText.brand, style = FoodType.RoundedCaption, color = FoodColors.Muted)
        }
        Text(text = state.title, style = FoodType.Hero, color = FoodColors.Ink, modifier = Modifier.semantics { heading() })
        if (state.subtitle.isNotEmpty()) Text(text = state.subtitle, style = FoodType.Body, color = FoodColors.Muted)
        if (state.status.isNotEmpty()) Text(text = state.status, style = FoodType.Status, color = FoodColors.Muted)
        if (state.roomCode.isNotEmpty()) FoodCard(bordered = true) {
            Row(Modifier.fillMaxWidth().padding(FoodSpacing.Large), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodSpacing.XXSmall)) {
                    Text(GroupText.roomCode, style = FoodType.RoundedCaption, color = FoodColors.Muted)
                    SelectionContainer {
                        Text(state.roomCode, style = FoodType.DialogTitle, color = FoodColors.Ink, modifier = Modifier.testTag("roomCode"))
                    }
                }
                IconButton(onClick = { controller.dispatch(GroupAction.SHARE_ROOM, "") }, enabled = !state.busy) {
                    Icon(Icons.Default.Share, "Invite people", tint = FoodColors.Orange)
                }
            }
        }
    }
}

@Composable
private fun GroupProgress(step: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = FoodSpacing.Medium).clearAndSetSemantics {
        contentDescription = "Step ${step + 1} of 4: ${GroupText.progressSteps[step]}"
    }, horizontalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
        GroupText.progressSteps.forEachIndexed { index, title ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
                Box(Modifier.fillMaxWidth().height(FoodSpacing.XXSmall).background(
                    if (index <= step) FoodColors.Success else FoodColors.Line, RoundedCornerShape(FoodRadius.Card)))
                Text(title, style = FoodType.Status, color = if (index == step) FoodColors.Ink else FoodColors.Muted)
            }
        }
    }
}

@Composable
private fun GroupOptions(state: GroupState, controller: GroupController, expanded: Boolean, toggle: () -> Unit) {
    FoodCard(bordered = true) {
        Column(Modifier.padding(FoodSpacing.Large), verticalArrangement = Arrangement.spacedBy(FoodSpacing.Large)) {
            TextButton(onClick = toggle, modifier = Modifier.fillMaxWidth().heightIn(min = FoodSize.TouchTarget).testTag("groupOptions")) {
                Text(when (state.page) {
                    GroupPage.CONNECT -> GroupText.manualConnection
                    GroupPage.LIBRARY -> GroupText.pasteMenu
                    else -> GroupText.roomOptions
                }, style = FoodType.Input, color = FoodColors.Ink, modifier = Modifier.weight(1f))
                Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = FoodColors.Muted)
            }
            if (expanded) {
                state.extraFields.forEach { GroupFieldContent(it, state.busy, controller) }
                state.utilityButtons.forEach { GroupActionButton(it, state.busy, controller) }
            }
        }
    }
}
