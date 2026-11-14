package com.banbiao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.collections.immutable.persistentListOf
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiChip
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiListTextFieldRow
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiStepper
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.basic.rememberMochiToastState
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListButton
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.picker.MochiTimeField
import ui.feice.mochi.theme.MochiTheme
import com.banbiao.app.core.NativeCore
import com.banbiao.app.data.AppState
import com.banbiao.app.data.AutoRule
import com.banbiao.app.data.CycleSegment
import com.banbiao.app.data.ShiftDef
import java.time.LocalDate

@Composable
internal fun ShiftsPage(state: AppState, update: (AppState) -> Unit) {
    val sheetState = rememberMochiSheetState()
    val toast = rememberMochiToastState()
    var editing by remember { mutableStateOf<ShiftDef?>(null) }
    var autoOpen by remember { mutableStateOf(false) }

    LaunchedEffect(editing, autoOpen) {
        if (editing != null || autoOpen) sheetState.show()
    }

    MochiLazyLargeTitleScaffold(
        title = "班次",
        subtitle = "定义你的班次与轮班节奏",
    ) {
        item {
            MochiListSection(
                header = "班次",
                footer = "点击班次可以改名、改时间或删除。",
            ) {
                if (state.shiftTypes.isEmpty()) {
                    MochiListRow(
                        title = "还没有班次",
                        subtitle = "先添加一个班次，才能在日历上排班",
                    )
                } else {
                    state.shiftTypes.forEach { def ->
                        MochiListRow(
                            title = def.name,
                            subtitle = def.timeLabel,
                            leading = { ShiftBadge(def) },
                            chevron = true,
                            onClick = { editing = def },
                        )
                    }
                }
            }
        }
        item {
            MochiButton(
                onClick = {
                    editing = ShiftDef(
                        id = "",
                        name = "",
                        start = "09:00",
                        end = "18:00",
                        tone = state.nextTone(),
                    )
                },
                style = MochiButtonStyle.ProminentGlass,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = 12.dp),
            ) { Text("添加班次") }
        }
        item {
            val autoBreakdown = state.rule?.segments?.joinToString(" · ") { segment ->
                "${state.shift(segment.shiftId)?.name ?: "?"} ${segment.days} 天"
            }
            val autoStatus = when {
                state.rule == null -> "未开启"
                !state.autoEnabled -> "已暂停"
                else -> "已开启"
            }
            MochiListSection(
                header = "轮班规则",
                footer = autoBreakdown?.let { text ->
                    "$autoStatus · $text"
                } ?: "自动规则只影响没有手动覆盖的日期。",
            ) {
                MochiListRow(
                    title = "自动轮班",
                    value = state.rule?.let {
                        if (state.autoEnabled) "每 ${it.totalDays} 天" else "已暂停"
                    } ?: "",
                    chevron = true,
                    enabled = state.shiftTypes.isNotEmpty(),
                    onClick = { autoOpen = true },
                )
            }
        }
    }

    MochiSheet(
        sheetState,
        onDismiss = {
            editing = null
            autoOpen = false
        },
    ) {
        val target = editing
        if (target != null) {
            EditShiftSheet(
                state = state,
                original = target,
                update = update,
                sheetState = sheetState,
                toast = toast,
            )
        } else if (autoOpen) {
            AutoShiftSheet(
                state = state,
                update = update,
                sheetState = sheetState,
                toast = toast,
            )
        }
    }
    MochiToastHost(toast)
}

@Composable
private fun EditShiftSheet(
    state: AppState,
    original: ShiftDef,
    update: (AppState) -> Unit,
    sheetState: ui.feice.mochi.basic.MochiSheetState,
    toast: ui.feice.mochi.basic.MochiToastState,
) {
    var name by remember(original.id) { mutableStateOf(original.name) }
    var start by remember(original.id) { mutableStateOf(original.start.ifBlank { "09:00" }) }
    var end by remember(original.id) { mutableStateOf(original.end.ifBlank { "18:00" }) }
    var rest by remember(original.id) { mutableStateOf(original.rest) }
    var confirmDelete by remember { mutableStateOf(false) }
    val isNew = original.id.isBlank()

    CadenceSheetScaffold(
        title = if (isNew) "添加班次" else "编辑班次",
        actions = {
            MochiIconButton(onClick = { sheetState.hide() }) {
                MochiIonicon("close", size = 20.dp)
            }
        },
    ) {
        MochiListSection(header = "名称") {
            MochiListTextFieldRow(
                value = name,
                onValueChange = { name = it.take(16) },
                label = "名称",
                placeholder = "例如 早班、晚班",
            )
        }
        MochiListSection(header = "类型", footer = "休息班显示为全天，不参与倒计时工时。") {
            MochiListRow(
                title = "班次类型",
                trailing = {
                    MochiSegmentedControl(
                        selectedIndex = if (rest) 1 else 0,
                        onSelect = { index -> rest = index == 1 },
                        items = persistentListOf("工作", "休息"),
                        modifier = Modifier.width(156.dp),
                    )
                },
            )
        }
        if (!rest) {
            MochiListSection(header = "时间", footer = "夜班可以跨天，例如 20:00–08:00。") {
                MochiListRow(
                    title = "开始",
                    trailing = {
                        MochiTimeField(
                            time = start.toMochiTime(),
                            onTimeChange = { value ->
                                val next = "%02d:%02d".format(value.hour, value.minute)
                                if (next == end) toast.show("开始和结束时间不能相同") else start = next
                            },
                            minuteInterval = 5,
                            format = { "%02d:%02d".format(it.hour, it.minute) },
                        )
                    },
                )
                MochiListRow(
                    title = "结束",
                    trailing = {
                        MochiTimeField(
                            time = end.toMochiTime(),
                            onTimeChange = { value ->
                                val next = "%02d:%02d".format(value.hour, value.minute)
                                if (next == start) toast.show("开始和结束时间不能相同") else end = next
                            },
                            minuteInterval = 5,
                            format = { "%02d:%02d".format(it.hour, it.minute) },
                        )
                    },
                )
            }
        }

        MochiButton(
            onClick = {
                val trimmed = name.trim()
                if (trimmed.isBlank()) {
                    toast.show("请输入班次名称")
                } else if (!rest && start == end) {
                    toast.show("开始和结束时间不能相同")
                } else {
                    val id = original.id.ifBlank { AppState.newShiftId() }
                    val def = original.copy(
                        id = id,
                        name = trimmed,
                        start = if (rest) "" else start,
                        end = if (rest) "" else end,
                        rest = rest,
                        tone = original.tone,
                    )
                    update(state.withShift(def))
                    toast.show(if (isNew) "已添加 ${trimmed}" else "已保存")
                    sheetState.hide()
                }
            },
            style = MochiButtonStyle.ProminentGlass,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("保存") }

        if (!isNew) {
            MochiButton(
                onClick = { confirmDelete = true },
                style = MochiButtonStyle.ClearGlass,
                modifier = Modifier.fillMaxWidth(),
            ) { Text("删除班次") }
        }
    }

    MochiDialog(
        visible = confirmDelete,
        onDismissRequest = { confirmDelete = false },
        title = "删除「${name.ifBlank { "这个班次" }}」？",
        message = "使用它的日期排班和轮班规则会一起移除。",
        actions = persistentListOf(
            MochiDialogAction("取消", MochiDialogActionRole.Cancel),
            MochiDialogAction("删除", MochiDialogActionRole.Default) {
                update(state.removeShift(original.id))
                confirmDelete = false
                toast.show("已删除班次")
                sheetState.hide()
            },
        ),
    )
}


@Composable
private fun ShiftChoiceGrid(
    defs: List<ShiftDef>,
    selectedId: String,
    onSelect: (String) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        defs.forEach { def ->
            val selected = def.id == selectedId
            Column(
                modifier = Modifier
                    .weight(1f)
                    .height(50.dp)
                    .clip(MochiTheme.shapes.small)
                    .background(
                        if (selected) MochiTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else MochiTheme.colorScheme.surfaceContainerLow,
                    )
                    .clickable { onSelect(def.id) }
                    .padding(vertical = 5.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    def.short,
                    style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                    color = if (selected) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurface,
                )
                Text(
                    def.name,
                    style = MochiTheme.textStyles.footnote.copy(fontSize = 9.sp),
                    color = if (selected) MochiTheme.colorScheme.primary else MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun CycleAnchorTile(
    day: Int,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MochiTheme.colorScheme
    Column(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(MochiListDefaults.SectionCorner))
            .background(
                if (selected) scheme.surface
                else scheme.surfaceContainerHigh,
            )
            .clickable(onClick = onClick)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$day",
            style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurface,
        )
        Text(
            "天",
            style = MochiTheme.textStyles.footnote.copy(fontSize = 9.sp),
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CyclePreviewTile(
    date: Int,
    short: String,
    def: ShiftDef?,
    modifier: Modifier = Modifier,
) {
    val scheme = MochiTheme.colorScheme
    Column(
        modifier
            .height(50.dp)
            .clip(RoundedCornerShape(MochiListDefaults.SectionCorner))
            .background(def?.let { shiftFill(it) } ?: scheme.surfaceContainerLowest)
            .padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            "$date",
            style = MochiTheme.textStyles.footnote,
            color = def?.let { shiftContent(it) } ?: scheme.onSurface,
        )
        Text(
            short,
            style = MochiTheme.textStyles.footnote.copy(fontSize = 9.sp),
            color = def?.let { shiftContent(it) } ?: scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun AutoShiftSheet(
    state: AppState,
    update: (AppState) -> Unit,
    sheetState: ui.feice.mochi.basic.MochiSheetState,
    toast: ui.feice.mochi.basic.MochiToastState,
) {
    val defs = state.shiftTypes
    val initial = state.rule?.segments?.filter { segment -> defs.any { it.id == segment.shiftId } }
    var segments by remember(state.rule, defs) {
        mutableStateOf(if (initial.isNullOrEmpty()) AppState.defaultSegments(defs) else initial)
    }
    var anchorIndex by remember(state.rule, defs) {
        mutableIntStateOf(state.rule?.anchorIndex ?: 0)
    }
    val total = segments.sumOf { it.days }.coerceAtLeast(1)
    val safeAnchor = anchorIndex.coerceIn(0, total - 1)
    val todayKey = LocalDate.now().toString()
    val draft = AutoRule(segments, todayKey, safeAnchor)
    val preview = if (segments.isEmpty()) emptyMap() else NativeCore.days(
        state.copy(rule = draft, autoEnabled = state.autoEnabled),
        todayKey,
        7,
    )
    var confirmDisable by remember { mutableStateOf(false) }

    CadenceSheetScaffold(
        title = "自动轮班",
        actions = {
            MochiIconButton(onClick = { sheetState.hide() }) {
                MochiIonicon("close", size = 20.dp)
            }
        },
    ) {
        if (defs.isEmpty() || segments.isEmpty()) {
            MochiListSection(header = "循环顺序") {
                MochiListRow(title = "还没有可用的班次", subtitle = "先回到班次页添加一个班次")
            }
        } else {
            MochiListSection(header = "循环顺序", footer = "每一段会连续排设定的天数。") {
                MochiListCard {
                    Column(
                        Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        segments.forEachIndexed { index, segment ->
                            Column(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(MochiListDefaults.SectionCorner))
                                    .background(
                                        MochiTheme.colorScheme.surfaceContainerLow.copy(
                                            alpha = if (MochiTheme.colorScheme.isDark) 0.55f else 0.30f,
                                        ),
                                    )
                                    .padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        "第 ${index + 1} 段",
                                        style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                                        modifier = Modifier.weight(1f),
                                    )
                                    MochiIconButton(
                                        onClick = {
                                            if (segments.size > 1) {
                                                segments = segments.filterIndexed { i, _ -> i != index }
                                                anchorIndex = 0
                                            }
                                        },
                                    ) { MochiIonicon("trash-outline", size = 18.dp) }
                                }
                                Text(
                                    "选择班次",
                                    style = MochiTheme.textStyles.footnote,
                                    color = MochiTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.padding(top = 2.dp),
                                )
                                ShiftChoiceGrid(
                                    defs = defs,
                                    selectedId = segment.shiftId,
                                    onSelect = { id ->
                                        segments = segments.mapIndexed { i, item ->
                                            if (i == index) item.copy(shiftId = id) else item
                                        }
                                    },
                                )
                                Row(
                                    Modifier.fillMaxWidth().padding(top = 2.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Text("持续", style = MochiTheme.textStyles.body)
                                    Spacer(Modifier.weight(1f))
                                    MochiStepper(
                                        value = segment.days,
                                        onValueChange = { days ->
                                            segments = segments.mapIndexed { i, item ->
                                                if (i == index) item.copy(days = days) else item
                                            }
                                            anchorIndex = 0
                                        },
                                        range = 1..60,
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(4.dp))
                        MochiButton(
                            onClick = {
                                val first = defs.first()
                                segments = segments + CycleSegment(first.id, 1)
                                anchorIndex = 0
                            },
                            style = MochiButtonStyle.Glass,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("添加一段") }
                    }
                }
            }

            MochiListSection(header = "今天是周期第几天") {
                MochiListCard {
                    Column(
                        Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        (0 until total).chunked(7).forEach { row ->
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                row.forEach { index ->
                                    CycleAnchorTile(
                                        day = index + 1,
                                        selected = index == safeAnchor,
                                        onClick = { anchorIndex = index },
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                repeat(7 - row.size) { Spacer(Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }

            MochiListSection(header = "接下来 7 天") {
                MochiListCard {
                    Row(
                        Modifier.fillMaxWidth().padding(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        repeat(7) { offset ->
                            val date = LocalDate.parse(todayKey).plusDays(offset.toLong())
                            val item = preview[date.toString()]
                            val def = state.shift(item?.shiftId)
                            CyclePreviewTile(
                                date = date.dayOfMonth,
                                short = def?.short ?: "—",
                                def = def,
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(12.dp))
            MochiButton(
                onClick = {
                    update(state.copy(rule = draft, autoEnabled = state.autoEnabled))
                    toast.show("轮班规则已更新")
                    sheetState.hide()
                },
                style = MochiButtonStyle.ProminentGlass,
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
            ) { Text("保存并更新") }

            if (state.rule != null) {
                Spacer(Modifier.height(8.dp))
                MochiButton(
                    onClick = { confirmDisable = true },
                    style = MochiButtonStyle.ClearGlass,
                    modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth(),
                ) { Text("关闭自动轮班") }
            }
        }
    }

    MochiDialog(
        visible = confirmDisable,
        onDismissRequest = { confirmDisable = false },
        title = "关闭自动轮班？",
        message = "自动班次会消失，手动排班仍然保留。",
        actions = persistentListOf(
            MochiDialogAction("取消", MochiDialogActionRole.Cancel),
            MochiDialogAction("关闭", MochiDialogActionRole.Default) {
                update(state.copy(rule = null, autoEnabled = true))
                confirmDisable = false
                toast.show("已关闭自动轮班")
                sheetState.hide()
            },
        ),
    )
}
