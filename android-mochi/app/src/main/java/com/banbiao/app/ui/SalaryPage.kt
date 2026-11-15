package com.banbiao.app.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.delay
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiDialog
import ui.feice.mochi.basic.MochiDialogAction
import ui.feice.mochi.basic.MochiDialogActionRole
import ui.feice.mochi.basic.MochiDialogTextField
import ui.feice.mochi.basic.MochiDropdown
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.MochiItem
import ui.feice.mochi.basic.MochiSegmentedControl
import ui.feice.mochi.basic.Text
import ui.feice.mochi.effect.content.MochiBlurCrossfade
import ui.feice.mochi.effect.content.MochiBlurText
import ui.feice.mochi.effect.content.MochiBlurTransitions
import ui.feice.mochi.effect.content.MochiCounterText
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListDefaults
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.shape.MochiRoundedRectangle
import ui.feice.mochi.theme.MochiTheme
import com.banbiao.app.data.AppState

private const val MIN_SALARY_YEAR = 2000
private const val MAX_SALARY_YEAR = 2100
private const val MAX_SALARY_CENTS = 100_000_000_000L // 10 亿元
private val INPUT_AMOUNT = Regex("^\\d{0,9}(\\.\\d{0,2})?$")

private data class SalaryActionState(
    val current: Boolean,
    val showBack: Boolean,
    val showEdit: Boolean,
    val hasRecord: Boolean,
)

@Composable
internal fun SalaryPage(state: AppState, update: (AppState) -> Unit) {
    val today = LocalDate.now()
    var year by rememberSaveable { mutableIntStateOf(today.year) }
    var month by rememberSaveable { mutableIntStateOf(today.monthValue) }
    var mode by rememberSaveable { mutableIntStateOf(0) } // 0 月 / 1 年
    var editingKey by remember { mutableStateOf<String?>(null) }
    var openMenu by remember { mutableStateOf<String?>(null) }

    val total = state.totalSalary()
    val selectedMonthKey = AppState.salaryKey(year, month)
    val selectedTotal = if (mode == 0) state.salaryOf(selectedMonthKey) else state.salaryYear(year)
    val recordedMonths = state.salaryMonths(year)
    val currentPeriod = year == today.year && (mode == 1 || month == today.monthValue)
    val actionState = SalaryActionState(
        current = currentPeriod,
        showBack = !currentPeriod,
        showEdit = mode == 0,
        hasRecord = selectedTotal > 0,
    )
    val yearItems = remember {
        (MIN_SALARY_YEAR..MAX_SALARY_YEAR).map { MochiItem("${it}年") }.toImmutableList()
    }
    val monthItems = remember { (1..12).map { MochiItem("${it}月") }.toImmutableList() }

    MochiLazyLargeTitleScaffold(
        title = "工资",
        subtitle = "记录每一份到手的工资",
    ) {
        item {
            SalarySummaryCard(total = total, months = state.salaries.size)
        }
        item {
            MochiListSection(
                header = "按年月查询",
                footer = if (mode == 0) {
                    "按月只统计所选月份；切换到按年可以看全年合计。"
                } else {
                    "按年汇总所选年份中已记录的所有月份。"
                },
            ) {
                Column(Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            "查看范围",
                            style = MochiTheme.textStyles.body,
                            color = MochiTheme.colorScheme.onSurface,
                            modifier = Modifier.weight(1f),
                        )
                        MochiSegmentedControl(
                            selectedIndex = mode,
                            onSelect = { mode = it },
                            items = persistentListOf("按月", "按年"),
                            modifier = Modifier.width(150.dp),
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(
                        Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        MochiIconButton(
                            onClick = {
                                if (mode == 0) {
                                    if (month == 1) {
                                        if (year > MIN_SALARY_YEAR) {
                                            year--
                                            month = 12
                                        }
                                    } else {
                                        month--
                                    }
                                } else if (year > MIN_SALARY_YEAR) {
                                    year--
                                }
                            },
                        ) { MochiIonicon("chevron-back", size = 20.dp) }

                        Row(
                            Modifier.weight(1f),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                        ) {
                            MochiDropdown(
                                expanded = openMenu == "year",
                                onExpandedChange = { openMenu = if (it) "year" else null },
                                items = yearItems,
                                onItemClick = { index ->
                                    year = MIN_SALARY_YEAR + index
                                },
                                selectedIndex = year - MIN_SALARY_YEAR,
                                modifier = Modifier.height(40.dp),
                            ) {
                                Text(
                                    "${year}年",
                                    style = MochiTheme.textStyles.body.copy(fontSize = 14.sp),
                                    color = MochiTheme.colorScheme.onSurface,
                                )
                                Spacer(Modifier.width(4.dp))
                                MochiIonicon(
                                    "chevron-down",
                                    size = 12.dp,
                                    tint = MochiTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            if (mode == 0) {
                                Spacer(Modifier.width(6.dp))
                                MochiDropdown(
                                    expanded = openMenu == "month",
                                    onExpandedChange = { openMenu = if (it) "month" else null },
                                    items = monthItems,
                                    onItemClick = { index ->
                                        month = index + 1
                                    },
                                    selectedIndex = month - 1,
                                    modifier = Modifier.height(40.dp),
                                ) {
                                    Text(
                                        "${month}月",
                                        style = MochiTheme.textStyles.body.copy(fontSize = 14.sp),
                                        color = MochiTheme.colorScheme.onSurface,
                                    )
                                    Spacer(Modifier.width(4.dp))
                                    MochiIonicon(
                                        "chevron-down",
                                        size = 12.dp,
                                        tint = MochiTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }

                        MochiIconButton(
                            onClick = {
                                if (mode == 0) {
                                    if (month == 12) {
                                        if (year < MAX_SALARY_YEAR) {
                                            year++
                                            month = 1
                                        }
                                    } else {
                                        month++
                                    }
                                } else if (year < MAX_SALARY_YEAR) {
                                    year++
                                }
                            },
                        ) { MochiIonicon("chevron-forward", size = 20.dp) }
                    }
                    Column(
                        Modifier.fillMaxWidth().padding(vertical = 20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        MochiCounterText(
                            text = formatSalary(selectedTotal),
                            direction = 1,
                            style = MochiTheme.textStyles.titleLarge.copy(
                                fontSize = if (formatSalary(selectedTotal).length > 14) 24.sp else 30.sp,
                                fontWeight = FontWeight.Bold,
                            ),
                            color = MochiTheme.colorScheme.primary,
                        )
                        Text(
                            if (mode == 0) {
                                if (selectedTotal > 0) "已记录" else "这个月还没有记录"
                            } else {
                                "${recordedMonths.size} 个月有记录"
                            },
                            style = MochiTheme.textStyles.footnote,
                            color = MochiTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 2.dp),
                        )
                    }
                    MochiBlurCrossfade(
                        targetState = actionState,
                        transition = MochiBlurTransitions.Replace,
                        modifier = Modifier.fillMaxWidth(),
                    ) { actions ->
                        if (actions.showBack || actions.showEdit) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                if (actions.showBack) {
                                    MochiButton(
                                        onClick = {
                                            mode = 0
                                            year = today.year
                                            month = today.monthValue
                                        },
                                        style = MochiButtonStyle.ClearGlass,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text("回到本月")
                                    }
                                }
                                if (actions.showEdit) {
                                    MochiButton(
                                        onClick = { editingKey = selectedMonthKey },
                                        style = MochiButtonStyle.ClearGlass,
                                        modifier = Modifier.weight(1f),
                                    ) {
                                        Text(
                                            when {
                                                actions.hasRecord && actions.current -> "修改这个月的工资"
                                                actions.hasRecord -> "修改"
                                                actions.current -> "录入这个月的工资"
                                                else -> "录入"
                                            },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        item {
            MochiListSection(
                header = "${year}年明细",
                footer = "工资仅保存在本机，卸载应用会一并删除。",
            ) {
                if (recordedMonths.isEmpty()) {
                    MochiListRow(
                        title = "还没有记录",
                        subtitle = "在首页底部记录当月工资",
                    )
                } else {
                    recordedMonths.forEach { (recordedMonth, cents) ->
                        MochiListRow(
                            title = "${recordedMonth}月",
                            subtitle = if (recordedMonth == today.monthValue && year == today.year) {
                                "本月 · 点击修改"
                            } else {
                                "点击修改"
                            },
                            value = formatSalary(cents),
                            chevron = true,
                            onClick = {
                                editingKey = AppState.salaryKey(year, recordedMonth)
                            },
                        )
                    }
                }
            }
        }
    }

    editingKey?.let { key ->
        SalaryEntryDialog(
            monthKey = key,
            initialCents = state.salaryOf(key),
            onDismiss = { editingKey = null },
            onSave = { cents ->
                update(state.withSalary(key, cents))
            },
        )
    }
}

@Composable
private fun SalarySummaryCard(total: Long, months: Int) {
    val scheme = MochiTheme.colorScheme
    val shape = remember { MochiRoundedRectangle(MochiListDefaults.SectionCorner) }
    val cardColor = MochiListDefaults.colors().container
    MochiListCard(
        colors = MochiListDefaults.colors(container = Color.Transparent),
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(cardColor)
            .background(
                Brush.verticalGradient(
                    listOf(
                        scheme.primary.copy(alpha = 0.16f),
                        Color.Transparent,
                    ),
                ),
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                MochiBlurText(
                    text = "累计工资",
                    style = MochiTheme.textStyles.subhead,
                    color = scheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .size(30.dp)
                        .clip(MochiTheme.shapes.small)
                        .background(scheme.primary.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center,
                ) {
                    MochiIonicon("wallet-outline", size = 17.dp, tint = scheme.primary)
                }
            }
            Spacer(Modifier.height(10.dp))
            MochiCounterText(
                text = formatSalary(total),
                direction = 1,
                style = MochiTheme.textStyles.displaySmall.copy(
                    fontSize = when {
                        formatSalary(total).length > 17 -> 22.sp
                        formatSalary(total).length > 14 -> 28.sp
                        else -> 36.sp
                    },
                    fontWeight = FontWeight.Bold,
                ),
                color = scheme.onSurface,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                if (months == 0) {
                    "还没有记录，去首页底部记一笔"
                } else {
                    "已记录 $months 个月 · 每一笔都算数"
                },
                style = MochiTheme.textStyles.footnote,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun SalaryEntryDialog(
    monthKey: String,
    initialCents: Long,
    onDismiss: () -> Unit,
    onSave: (Long) -> Unit,
) {
    var visible by remember(monthKey) { mutableStateOf(true) }
    var value by remember(monthKey) {
        mutableStateOf(
            if (initialCents > 0) {
                BigDecimal.valueOf(initialCents, 2).toPlainString()
            } else {
                ""
            },
        )
    }
    LaunchedEffect(visible) {
        if (!visible) {
            delay(600)
            onDismiss()
        }
    }
    BackHandler(enabled = visible) { visible = false }
    val cents = parseSalaryInput(value)
    val error = when {
        value.isNotBlank() && cents == null -> "请输入有效金额"
        cents != null && cents > MAX_SALARY_CENTS -> "金额过大"
        else -> null
    }
    val canSave = cents != null && error == null
    val year = monthKey.substringBefore('-').toIntOrNull() ?: 0
    val month = monthKey.substringAfter('-').toIntOrNull() ?: 0

    MochiDialog(
        visible = visible,
        onDismissRequest = { if (visible) visible = false },
        title = "记录 ${year}年${month}月工资",
        message = "输入实际到手的金额，数据只保存在本机。",
        actions = persistentListOf(
            MochiDialogAction(
                label = "取消",
                role = MochiDialogActionRole.Cancel,
                enabled = true,
                onClick = { if (visible) visible = false },
            ),
            MochiDialogAction(
                label = "保存",
                role = MochiDialogActionRole.Default,
                enabled = canSave,
                onClick = {
                    val amount = cents
                    if (visible && amount != null) {
                        onSave(amount)
                        visible = false
                    }
                },
            ),
        ),
        content = {
            MochiDialogTextField(
                value = value,
                onValueChange = { input ->
                    val cleaned = input.replace(",", "").replace("，", "")
                    if (cleaned.isBlank() || cleaned.matches(INPUT_AMOUNT)) {
                        value = cleaned
                    }
                },
                placeholder = "例如 12000",
                error = error,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        val amount = cents
                        if (visible && amount != null) {
                            onSave(amount)
                            visible = false
                        }
                    },
                ),
            )
            Text(
                "单位：元，可输入两位小数。输入 0 可清除这个月的记录。",
                style = MochiTheme.textStyles.footnote.copy(textAlign = TextAlign.Center),
                color = MochiTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
        },
    )
}

internal fun formatSalary(cents: Long): String {
    val safe = cents.coerceAtLeast(0L)
    val yuan = safe / 100
    val fen = (safe % 100).toInt()
    return "¥${groupDigits(yuan)}.${fen.toString().padStart(2, '0')}"
}

private fun groupDigits(value: Long): String =
    value.toString().reversed().chunked(3).joinToString(",").reversed()

private fun parseSalaryInput(value: String): Long? {
    val text = value.trim()
    if (text.isBlank() || text == ".") return null
    val amount = text.toBigDecimalOrNull() ?: return null
    if (amount < BigDecimal.ZERO) return null
    return runCatching {
        amount.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact()
    }.getOrNull()
}
