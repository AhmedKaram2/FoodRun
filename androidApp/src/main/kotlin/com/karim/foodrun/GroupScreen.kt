package com.karim.foodrun

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.LayoutDirection
import com.google.zxing.BarcodeFormat
import com.journeyapps.barcodescanner.BarcodeEncoder
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
    LaunchedEffect(state.accessBlocked) {
        while (state.accessBlocked) { kotlinx.coroutines.delay(1000); controller.tickAccessBlock() }
    }
    val focus = LocalFocusManager.current
    LaunchedEffect(state.page) { focus.clearFocus() }
    BackHandler(state.canGoBack) { controller.dispatch(GroupAction.BACK, "") }
    CompositionLocalProvider(LocalLayoutDirection provides if(state.rtl) LayoutDirection.Rtl else LayoutDirection.Ltr) {
    if (state.page == GroupPage.QUICK_SPIN) {
        Column {
            TextButton(
                onClick = { controller.dispatch(GroupAction.BACK, "") },
                modifier = Modifier.statusBarsPadding(),
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null)
                Text(GroupText.localized(GroupText.backToRooms, state.rtl), modifier = Modifier.padding(start = FoodSpacing.XSmall))
            }
            Box(Modifier.weight(1f)) { FoodRunScreen() }
        }
    } else {
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
                Text(GroupText.localized(GroupText.working, state.rtl), style = FoodType.Status, color = FoodColors.Muted, modifier = Modifier.padding(FoodSpacing.XSmall))
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
                    if (state.progressStep >= 0) item(key = "steps") { GroupProgress(state.progressStep, state.rtl) }
                    state.wheel?.let { wheel -> item(key = "wheel:${wheel.round.id}") { GroupWheelContent(wheel) } }
                    items(state.topCards, key = { "top-card:${it.id}" }) { GroupCardContent(it, state.busy, controller) }
                    items(state.mainFields, key = { "field:${it.key.name}" }) { GroupFieldContent(it, state.busy, controller) }
                    if (state.page != GroupPage.ROOM && state.extraFields.isNotEmpty()) item(key = "extras") {
                        GroupOptions(state, controller, optionsExpanded) { optionsExpanded = !optionsExpanded }
                    }
                    items(state.inlineButtons.filter { it.action != GroupAction.SET_LANGUAGE }, key = { "action:${it.action.name}:${it.value}" }) { GroupActionButton(it, state.busy, controller, prominent = false) }
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
    }
}

@Composable
private fun GroupHeader(state: GroupState, controller: GroupController) {
    Column(verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            if (state.canGoBack) TextButton(onClick = { controller.dispatch(GroupAction.BACK, "") }) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, null, modifier = Modifier.size(FoodSize.IconSmall))
                Text(text = GroupText.localized(GroupText.back, state.rtl), modifier = Modifier.padding(start = FoodSpacing.XSmall), color = FoodColors.Orange)
            }
            Spacer(Modifier.weight(1f))
            GroupLanguagePicker(state, controller)
        }
        Text(text = state.title, style = FoodType.Hero, color = FoodColors.Ink, modifier = Modifier.semantics { heading() })
        if (state.subtitle.isNotEmpty()) Text(text = state.subtitle, style = FoodType.Body, color = FoodColors.Muted)
        if (state.status.isNotEmpty()) Text(text = state.status, style = FoodType.Status, color = FoodColors.Muted)
        if (state.roomCode.isNotEmpty()) FoodCard(bordered = true) {
            Column(Modifier.fillMaxWidth().padding(FoodSpacing.Large), verticalArrangement = Arrangement.spacedBy(FoodSpacing.Medium), horizontalAlignment = Alignment.CenterHorizontally) {
                if (state.inviteSummary.isNotEmpty()) Text(state.inviteSummary, style = FoodType.Body, color = FoodColors.Ink)
                if (state.inviteLink.isNotEmpty()) remember(state.inviteLink) {
                    runCatching { BarcodeEncoder().encodeBitmap(state.inviteLink, BarcodeFormat.QR_CODE, 480, 480) }.getOrNull()
                }?.let { bitmap ->
                    Image(bitmap.asImageBitmap(), "Scan to join ${state.title}", modifier = Modifier.size(144.dp))
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodSpacing.XXSmall)) {
                        Text("SCAN, TAP, OR ENTER", style = FoodType.RoundedCaption, color = FoodColors.Muted)
                        SelectionContainer {
                            Text(state.roomCode, style = FoodType.DialogTitle, color = FoodColors.Ink, modifier = Modifier.testTag("roomCode"))
                        }
                    }
                    IconButton(onClick = { controller.dispatch(GroupAction.SHARE_ROOM, "") }, enabled = !state.busy) {
                        Icon(Icons.Default.Share, "Share invitation link", tint = FoodColors.Orange)
                    }
                }
            }
        }
    }
}

@Composable
private fun GroupProgress(step: Int, rtl: Boolean) {
    Row(Modifier.fillMaxWidth().padding(vertical = FoodSpacing.Medium).clearAndSetSemantics {
        contentDescription = if(rtl) "الخطوة ${step + 1} من ${GroupText.progressSteps.size}: ${GroupText.localized(GroupText.progressSteps[step], true)}" else "Step ${step + 1} of ${GroupText.progressSteps.size}: ${GroupText.progressSteps[step]}"
    }, horizontalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
        GroupText.progressSteps.forEachIndexed { index, title ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(FoodSpacing.XSmall)) {
                Box(Modifier.fillMaxWidth().height(FoodSpacing.XXSmall).background(
                    if (index <= step) FoodColors.Success else FoodColors.Line, RoundedCornerShape(FoodRadius.Card)))
                Text(GroupText.localized(title, rtl), style = FoodType.Status, color = if (index == step) FoodColors.Ink else FoodColors.Muted)
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
                    GroupPage.CONNECT -> GroupText.localized(GroupText.manualConnection, state.rtl)
                    GroupPage.LIBRARY -> GroupText.localized(GroupText.pasteMenu, state.rtl)
                    GroupPage.SETUP -> if (state.rtl) "خيارات إضافية · التصويت والرسوم" else "More options · voting and fees"
                    else -> GroupText.localized(GroupText.roomOptions, state.rtl)
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
