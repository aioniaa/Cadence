package com.banbiao.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ui.feice.mochi.basic.MochiBarButton
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiBarIconButton
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.MochiToastHost
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.basic.rememberMochiToastState
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiTabScaffoldDefaults
import ui.feice.mochi.picker.MochiCalendar
import ui.feice.mochi.picker.MochiDate
import ui.feice.mochi.picker.MochiDateSelection
import ui.feice.mochi.theme.MochiTheme
import com.banbiao.app.core.Countdown
import com.banbiao.app.core.MonthCell
import com.banbiao.app.core.NativeCore
import com.banbiao.app.data.AppState
import com.banbiao.app.data.ShiftDef
import java.time.LocalDate
import java.time.LocalDateTime
import kotlin.math.min

@Composable
internal fun HomePage(
    state: AppState,
    update: (AppState) -> Unit,
    onOpenShifts: () -> Unit,
) {
    val today = LocalDate.now()
    val todayKey = today.toString()
    var viewYear by rememberSaveable { mutableIntStateOf(today.year) }
    var viewMonth by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var selectedKey by rememberSaveable { mutableStateOf(todayKey) }
    var dayOpen by remember { mutableStateOf(false) }
    var rangeSelection by remember { mutableStateOf<MochiDateSelection?>(null) }
    val sheetState = rememberMochiSheetState()
    val toast = rememberMochiToastState()

    val cells = remember(state, viewYear, viewMonth) {
        NativeCore.month(state, viewYear, viewMonth)
    }
    val selectedDate = runCatching { LocalDate.parse(selectedKey) }.getOrNull() ?: today
    val leave = state.isLeave(selectedKey)
    val effectiveId = if (leave) null else state.assignments[selectedKey]
        ?: cells.firstOrNull { it.key == selectedKey }?.shiftId

    LaunchedEffect(dayOpen) {
        if (dayOpen) sheetState.show()
    }

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 10.dp, vertical = 8.dp)
                .padding(bottom = MochiTabScaffoldDefaults.contentBottomPadding()),
        ) {
            SunsetCalendarCard(
                state = state,
                cells = cells,
                today = today,
                todayKey = todayKey,
                year = viewYear,
                month = viewMonth,
                selectedKey = selectedKey,
                onPrev = {
                    if (--viewMonth < 1) {
                        viewMonth = 12
                        viewYear--
                    }
                },
                onNext = {
                    if (++viewMonth > 12) {
                        viewMonth = 1
                        viewYear++
                    }
                },
                onToday = {
                    viewYear = today.year
                    viewMonth = today.monthValue
                    selectedKey = todayKey
                },
                onSelect = { key ->
                    selectedKey = key
                    rangeSelection = null
                    dayOpen = true
                },
            )
        }

        MochiSheet(
            sheetState,
            onDismiss = {
                dayOpen = false
                rangeSelection = null
            },
        ) {
            CadenceSheetScaffold(
                title = "${selectedDate.monthValue}月${selectedDate.dayOfMonth}日",
                navigationIcon = {
                    MochiBarIconButton(
                        onClick = { sheetState.hide() },
                        navId = "day.close",
                        contentKey = "close",
                    ) { MochiIonicon("close", size = 20.dp) }
                },
                actions = {
                    if (rangeSelection != null) {
                        MochiBarButton(
                            "确认",
                            onClick = {
                                val keys = rangeKeys(rangeSelection)
                                if (keys.isNotEmpty()) {
                                    update(
                                        state.copy(
                                            leaves = state.leaves + keys,
                                            assignments = state.assignments - keys,
                                        ),
                                    )
                                    toast.show("已设为 ${keys.size} 天休假")
                                    rangeSelection = null
                                    sheetState.hide()
                                }
                            },
                        )
                    }
                },
            ) {
                if (state.shiftTypes.isEmpty()) {
                    MochiListSection(header = "选择班次", footer = "先在「班次」页创建一个班次。") {
                        MochiListRow(title = "还没有班次", subtitle = "添加后就能给日期排班")
                        MochiButton(
                            onClick = {
                                sheetState.hide()
                                onOpenShifts()
                            },
                            style = MochiButtonStyle.ProminentGlass,
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("前往班次") }
                    }
                } else {
                    MochiListSection(header = "选择班次", footer = "点击日期即可修改；没有外框，只有底色变化。") {
                        state.shiftTypes.forEach { def ->
                            MochiListRow(
                                title = def.name,
                                subtitle = def.timeLabel,
                                leading = { ShiftBadge(def) },
                                checked = !leave && effectiveId == def.id,
                                onClick = {
                                    update(
                                        state.copy(
                                            assignments = state.assignments + (selectedKey to def.id),
                                            leaves = state.leaves - selectedKey,
                                        ),
                                    )
                                    rangeSelection = null
                                    toast.show("已设为${def.name}")
                                    sheetState.hide()
                                },
                            )
                        }
                    }
                    MochiListSection(
                        header = "特殊情况",
                        footer = "当天不排班，轮班暂停；休假后自动轮班会从上次班次继续，回来那天直接点一个班次即可手动指定。",
                    ) {
                        if (state.rule != null) {
                            MochiListRow(
                                title = "自动轮班",
                                subtitle = if (state.autoEnabled) "已开启，未手动覆盖的日期自动排班" else "已暂停，规则仍保留",
                                checked = state.autoEnabled,
                                onClick = {
                                    update(state.copy(autoEnabled = !state.autoEnabled))
                                },
                            )
                        }
                        val leaveExpanded = rangeSelection != null
                        val chevronAngle by animateFloatAsState(
                            targetValue = if (leaveExpanded) 90f else 0f,
                            animationSpec = tween(220),
                            label = "leave-chevron",
                        )
                        MochiListRow(
                            title = "休假",
                            leading = { LeaveBadge() },
                            trailing = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (leave) {
                                        Text(
                                            "已休假",
                                            style = MochiTheme.textStyles.footnote,
                                            color = MochiTheme.colorScheme.onSurfaceVariant,
                                        )
                                        Spacer(Modifier.width(6.dp))
                                    }
                                    Box(Modifier.rotate(chevronAngle)) {
                                        MochiIonicon("chevron-forward", size = 14.dp)
                                    }
                                }
                            },
                            colors = MochiListDefaults.colors(divider = Color.Transparent),
                            onClick = {
                                if (leaveExpanded) {
                                    rangeSelection = null
                                } else {
                                    rangeSelection = MochiDateSelection.Range(
                                        MochiDate(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                        MochiDate(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                    )
                                    toast.show("选择日期后点右上角确认")
                                }
                            },
                        )
                        if (leaveExpanded) {
                            val expandState = remember {
                                MutableTransitionState(false).apply { targetState = true }
                            }
                            AnimatedVisibility(
                                visibleState = expandState,
                                enter = fadeIn(tween(180)),
                                exit = fadeOut(tween(120)),
                            ) {
                                Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                                    MochiCalendar(
                                        selection = rangeSelection ?: MochiDateSelection.Range(
                                            MochiDate(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                            MochiDate(selectedDate.year, selectedDate.monthValue, selectedDate.dayOfMonth),
                                        ),
                                        onSelectionChange = { rangeSelection = it },
                                        modifier = Modifier.fillMaxWidth(),
                                    )
                                    if (leave) {
                                        Spacer(Modifier.height(8.dp))
                                        MochiButton(
                                            onClick = {
                                                update(state.copy(leaves = state.leaves - selectedKey))
                                                rangeSelection = null
                                                toast.show("已取消休假")
                                            },
                                            style = MochiButtonStyle.ClearGlass,
                                            modifier = Modifier.fillMaxWidth(),
                                        ) { Text("取消这天的休假") }
                                    }
                                }
                            }
                        }
                    }
                    if (!leave && state.assignments.containsKey(selectedKey)) {
                        Spacer(Modifier.height(8.dp))
                        MochiButton(
                            onClick = {
                                update(state.copy(assignments = state.assignments - selectedKey))
                                toast.show(if (state.rule != null) "已恢复自动班次" else "已清除")
                                sheetState.hide()
                            },
                            style = MochiButtonStyle.ClearGlass,
                            modifier = Modifier.padding(horizontal = 24.dp).fillMaxWidth(),
                        ) { Text(if (state.rule != null) "恢复自动班次" else "清除排班") }
                    } else if (!leave && state.rule != null) {
                        MochiListRow(
                            title = "跟随自动排班",
                            subtitle = "这一天的班次来自轮班规则",
                            checked = true,
                        )
                    }
                }
            }
        }
        MochiToastHost(toast)
    }
}

private fun rangeKeys(selection: MochiDateSelection?): Set<String> {
    val range = selection as? MochiDateSelection.Range ?: return emptySet()
    val startDate = range.start ?: return emptySet()
    val endDate = range.end ?: return emptySet()
    val start = LocalDate.of(startDate.year, startDate.month, startDate.day)
    val end = LocalDate.of(endDate.year, endDate.month, endDate.day)
    val from = minOf(start, end)
    val to = maxOf(start, end)
    return generateSequence(from) { date ->
        if (date.isBefore(to)) date.plusDays(1) else null
    }.map { it.toString() }.toSet()
}

@Composable
private fun SunsetCalendarCard(
    state: AppState,
    cells: List<MonthCell>,
    today: LocalDate,
    todayKey: String,
    year: Int,
    month: Int,
    selectedKey: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
    onSelect: (String) -> Unit,
) {
    var dragAmount by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current
    val switchThreshold = with(density) { 48.dp.toPx() }
    val previous by rememberUpdatedState(onPrev)
    val next by rememberUpdatedState(onNext)

    Column(Modifier.fillMaxWidth()) {
        SunsetHero(state, today)
        Column(
            Modifier
                .fillMaxWidth()
                .padding(10.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures(
                        onDragStart = { dragAmount = 0f },
                        onHorizontalDrag = { change, amount ->
                            dragAmount += amount
                            change.consume()
                        },
                        onDragEnd = {
                            if (dragAmount > switchThreshold) previous()
                            else if (dragAmount < -switchThreshold) next()
                            dragAmount = 0f
                        },
                        onDragCancel = { dragAmount = 0f },
                    )
                },
        ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        MochiBlurText(
                            text = "${year}年${month}月",
                            style = MochiTheme.textStyles.titleLarge,
                            color = MochiTheme.colorScheme.onSurface,
                        )
                        Text(
                            "MONTHLY SHIFT · 左右滑动切换月份",
                            style = MochiTheme.textStyles.footnote.copy(
                                letterSpacing = 1.1.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            color = MochiTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.72f),
                        )
                    }
                    MochiButton(onClick = onToday, style = MochiButtonStyle.ClearGlass) {
                        Text("今天")
                    }
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth()) {
                    listOf("一", "二", "三", "四", "五", "六", "日").forEach { label ->
                        Text(
                            label,
                            modifier = Modifier.weight(1f),
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                            style = MochiTheme.textStyles.footnote.copy(textAlign = TextAlign.Center),
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                cells.chunked(7).forEach { week ->
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        week.forEach { cell ->
                            DayCell(
                                cell = cell,
                                def = state.shift(cell.shiftId),
                                selected = cell.key == selectedKey,
                                today = cell.key == todayKey,
                                leave = state.isLeave(cell.key),
                                onClick = { onSelect(cell.key) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(7 - week.size) { Spacer(Modifier.weight(1f)) }
                    }
                    Spacer(Modifier.height(2.dp))
                }
                Spacer(Modifier.height(8.dp))
                TodayShiftCard(state = state, todayKey = todayKey)
                Spacer(Modifier.height(6.dp))
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MochiBlurText(
                        text = "本月 ${cells.count { it.inMonth && it.shiftId != null }} 天",
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.weight(1f))
                    Text(
                        "班次深浅区分",
                        style = MochiTheme.textStyles.footnote,
                        color = MochiTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
}

@Composable
private fun SunsetHero(state: AppState, today: LocalDate) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(1000)
            now = LocalDateTime.now()
        }
    }
    val countdown = NativeCore.countdown(state, now.toLocalDate(), now.toLocalTime())
    val clock = countdown.kind == "active" ||
        countdown.kind == "before" ||
        (countdown.kind == "rest" && countdown.seconds > 0)
    // Active shifts drain; waiting for the next shift fills over a 24h window.
    val liquidTarget = when (countdown.kind) {
        "active" -> countdown.progress.coerceIn(0f, 1f)
        "before", "rest" -> (1f - (countdown.seconds / 86400f)).coerceIn(0f, 1f)
        "after", "leave" -> 0f
        else -> 0.30f
    }
    val liquid by animateFloatAsState(
        targetValue = liquidTarget,
        animationSpec = tween(900),
        label = "soda-level",
    )
    val clockColor by animateColorAsState(
        targetValue = lerp(
            MochiTheme.colorScheme.onSurface,
            MochiTheme.colorScheme.primary,
            0.14f + 0.30f * liquid,
        ),
        animationSpec = tween(900),
        label = "clock-color",
    )

    val sodaPhase by rememberInfiniteTransition(label = "soda").animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(6200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "soda-phase",
    )

    Box(Modifier.fillMaxWidth().height(220.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .blur(6.dp, BlurredEdgeTreatment.Unbounded),
        ) {
            SodaBackdrop(liquid, sodaPhase)
        }
        MochiBlurText(
            text = countdown.label,
            style = MochiTheme.textStyles.subhead,
            color = clockColor,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 18.dp),
        )
        if (clock) {
            RoundDotCountdown(
                seconds = countdown.seconds,
                color = clockColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 20.dp),
            )
        } else {
            MochiBlurText(
                text = displayCountdown(countdown),
                style = MochiTheme.textStyles.titleLarge.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 38.sp,
                ),
                color = clockColor,
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = 20.dp),
            )
        }
    }
}

@Composable
private fun RoundDotCountdown(
    seconds: Int,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val total = seconds.coerceAtLeast(0)
    val style = MochiTheme.textStyles.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 42.sp,
    )
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        ClockPart("%02d".format(total / 3600), style, color)
        RoundDotColon(color)
        ClockPart("%02d".format(total % 3600 / 60), style, color)
        RoundDotColon(color)
        ClockPart("%02d".format(total % 60), style, color)
    }
}

@Composable
private fun ClockPart(text: String, style: androidx.compose.ui.text.TextStyle, color: Color) {
    MochiCounterText(
        text = text,
        direction = -1,
        style = style,
        color = color,
    )
}

@Composable
private fun RoundDotColon(color: Color) {
    Column(
        Modifier.width(14.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        repeat(2) {
            Box(
                Modifier
                    .size(4.dp)
                    .clip(CircleShape)
                    .background(color),
            )
        }
    }
}

private fun displayCountdown(countdown: Countdown): String = when (countdown.kind) {
    "rest" -> "休息"
    "after" -> "已下班"
    "leave" -> "休假"
    else -> "—"
}

@Composable
private fun SodaBackdrop(progress: Float, bubblePhase: Float) {
    val scheme = MochiTheme.colorScheme
    Canvas(Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h * 0.96f
        val radius = min(w * 0.47f, h * 0.82f)
        val p = progress.coerceIn(0f, 1f)
        val top = cy - radius
        val fillTop = cy - radius * p
        val dome = Path().apply {
            addArc(
                Rect(cx - radius, cy - radius, cx + radius, cy + radius),
                180f,
                180f,
            )
            close()
        }

        clipPath(dome) {
            // Soda glass / empty part.
            drawRect(
                brush = Brush.verticalGradient(
                    colors = listOf(
                        scheme.surfaceContainerLow,
                        scheme.primary.copy(alpha = 0.08f),
                        scheme.primary.copy(alpha = 0.14f),
                    ),
                    startY = top,
                    endY = cy,
                ),
                topLeft = Offset(cx - radius, top),
                size = Size(radius * 2f, radius),
            )

            if (p > 0.002f) {
                // Liquid with a small carbonated wave.
                val waveHeight = 4.dp.toPx()
                val liquid = Path().apply {
                    moveTo(cx - radius, fillTop)
                    val steps = 48
                    for (i in 0..steps) {
                        val x = cx - radius + (radius * 2f) * i / steps
                        val y = fillTop + kotlin.math.sin(
                            (i.toFloat() / steps) * Math.PI.toFloat() * 4f + bubblePhase * Math.PI.toFloat() * 2f,
                        ) * waveHeight
                        lineTo(x, y)
                    }
                    lineTo(cx + radius, cy)
                    lineTo(cx - radius, cy)
                    close()
                }
                drawPath(
                    path = liquid,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            scheme.primary.copy(alpha = 0.18f),
                            scheme.primary.copy(alpha = 0.30f),
                        ),
                        startY = fillTop,
                        endY = cy,
                    ),
                )

                // Carbonation bubbles, kept inside the liquid.
                repeat(18) { i ->
                    val x = cx - radius * 0.82f +
                        (radius * 1.64f) * ((i * 0.37f + bubblePhase * 0.13f) % 1f)
                    val travel = (bubblePhase + i * 0.137f) % 1f
                    val y = cy - (cy - fillTop) * travel
                    val r = (1.2f + (i % 3) * 0.7f).dp.toPx()
                    drawCircle(
                        color = scheme.surface.copy(alpha = 0.16f + (i % 4) * 0.03f),
                        radius = r,
                        center = Offset(x, y),
                    )
                }
            }
        }
    }
}

@Composable
private fun TodayShiftCard(state: AppState, todayKey: String) {
    val todayCell = remember(state, todayKey) {
        NativeCore.days(state, todayKey, 1)[todayKey]
    }
    val leave = state.isLeave(todayKey)
    val def = if (leave) null else state.shift(state.assignments[todayKey] ?: todayCell?.shiftId)
    if (def == null && !leave) return
    val scheme = MochiTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().padding(top = 1.dp),
        horizontalArrangement = Arrangement.Center,
    ) {
        Column(
            Modifier
                .clip(MochiTheme.shapes.small)
                .background(scheme.surfaceContainerLow.copy(alpha = 0.62f))
                .padding(horizontal = 14.dp, vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                if (leave) "今日 · 休假" else "今日 · ${def?.name}",
                style = MochiTheme.textStyles.labelSmall.copy(fontWeight = FontWeight.Medium),
                color = scheme.onSurfaceVariant.copy(alpha = 0.72f),
            )
            Text(
                if (leave) "轮班暂停" else if (def?.rest == true) "全天休息" else def?.timeLabel.orEmpty(),
                style = MochiTheme.textStyles.labelSmall,
                color = scheme.onSurfaceVariant.copy(alpha = 0.54f),
                modifier = Modifier.padding(top = 1.dp),
            )
        }
    }
}

@Composable
private fun LeaveBadge() {
    val scheme = MochiTheme.colorScheme
    Box(
        Modifier
            .size(28.dp)
            .clip(MochiTheme.shapes.small)
            .background(scheme.surfaceContainerLow),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "假",
            style = MochiTheme.textStyles.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun DayCell(
    cell: MonthCell,
    def: ShiftDef?,
    selected: Boolean,
    today: Boolean,
    leave: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MochiTheme.colorScheme
    val shape = MochiTheme.shapes.small
    val baseTarget = when {
        leave -> scheme.surfaceContainerLow.copy(alpha = 0.62f)
        def == null -> Color.Transparent
        else -> shiftFill(def)
    }
    val base by animateColorAsState(baseTarget, label = "day-base")
    val todayLayer by animateColorAsState(
        if (today) scheme.primary.copy(alpha = 0.12f) else Color.Transparent,
        label = "day-today",
    )
    val selectedLayer by animateColorAsState(
        if (selected) scheme.primary.copy(alpha = 0.22f) else Color.Transparent,
        label = "day-selected",
    )
    Box(
        modifier
            .aspectRatio(1f)
            .alpha(if (cell.inMonth) 1f else 0.42f)
            .clip(shape)
            .background(base)
            .background(todayLayer)
            .background(selectedLayer)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "${cell.key} ${def?.name ?: "未排班"}"
                this.selected = selected
                role = Role.Button
            }
            .padding(5.dp),
    ) {
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
            Text(
                cell.day.toString(),
                style = MochiTheme.textStyles.subhead.copy(
                    fontWeight = if (today) FontWeight.Bold else FontWeight.Medium,
                    fontSize = 14.sp,
                ),
                color = when {
                    today -> scheme.primary
                    leave -> scheme.onSurfaceVariant
                    def != null -> shiftContent(def)
                    else -> scheme.onSurface
                },
            )
            Text(
                if (leave) "假" else def?.short ?: "",
                style = MochiTheme.textStyles.footnote.copy(fontSize = 9.sp),
                color = if (leave) scheme.onSurfaceVariant
                else if (def != null) shiftContent(def) else scheme.onSurfaceVariant,
            )
        }
        if (today) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 3.dp)
                    .width(13.dp)
                    .height(2.dp)
                    .clip(CircleShape)
                    .background(scheme.primary),
            )
        }
    }
}
