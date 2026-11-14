package com.banbiao.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiButton
import ui.feice.mochi.basic.MochiButtonStyle
import ui.feice.mochi.basic.MochiSheet
import ui.feice.mochi.basic.Text
import ui.feice.mochi.basic.rememberMochiSheetState
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.list.MochiListCard
import ui.feice.mochi.list.MochiListRow
import ui.feice.mochi.list.MochiListSection
import ui.feice.mochi.navigation.MochiLazyLargeTitleScaffold
import ui.feice.mochi.theme.MochiTheme
import com.banbiao.app.data.AppInfo
import com.banbiao.app.data.AppState
import com.banbiao.app.data.ThemeMode

@Composable
internal fun SettingsPage(
    state: AppState,
    changeTheme: (ThemeMode) -> Unit,
    dark: Boolean,
    onToggleIsland: (Boolean) -> Unit,
) {
    val sheetState = rememberMochiSheetState()
    var changelogOpen by remember { mutableStateOf(false) }

    LaunchedEffect(changelogOpen) {
        if (changelogOpen) sheetState.show()
    }

    MochiLazyLargeTitleScaffold(
        title = "设置",
        subtitle = "外观与应用信息",
    ) {
        item {
            MochiListSection(
                header = "外观",
                footer = "点击按钮切换浅色/深色；玫瑰强调色 + 灰度背景。",
            ) {
                MochiListCard {
                    Row(
                        Modifier.fillMaxWidth().padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                "界面主题",
                                style = MochiTheme.textStyles.body,
                                color = MochiTheme.colorScheme.onSurface,
                            )
                            Text(
                                when (state.themeMode) {
                                    ThemeMode.System -> "当前：跟随系统（${if (dark) "深色" else "浅色"}）"
                                    ThemeMode.Light -> "当前：浅色"
                                    ThemeMode.Dark -> "当前：深色"
                                },
                                style = MochiTheme.textStyles.footnote,
                                color = MochiTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 2.dp),
                            )
                        }
                        MochiButton(
                            onClick = {
                                changeTheme(if (dark) ThemeMode.Light else ThemeMode.Dark)
                            },
                            style = MochiButtonStyle.ProminentGlass,
                        ) {
                            MochiIonicon(
                                if (dark) "sunny-outline" else "moon-outline",
                                size = 18.dp,
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(if (dark) "浅色" else "深色")
                        }
                    }
                }
            }
        }
        item {
            MochiListSection(
                header = "超级岛",
                footer = "开启后使用小米焦点通知 miui.focus.param；需小米后台为应用开通焦点通知权限，否则会退化为普通通知。",
            ) {
                MochiListRow(
                    title = "上岛显示下班倒计时",
                    subtitle = if (state.islandEnabled) "已开启" else "未开启",
                    checked = state.islandEnabled,
                    onClick = { onToggleIsland(!state.islandEnabled) },
                )
            }
        }
        item {
            MochiListSection(header = "关于") {
                MochiListRow(title = "应用", value = "Cadence")
                MochiListRow(title = "版本", value = AppInfo.version)
                MochiListRow(title = "配色", value = "玫瑰 + 灰度")
                MochiListRow(title = "数据", value = "仅保存在本机")
                MochiListRow(
                    title = "更新日志",
                    subtitle = "版本迭代与修复记录",
                    chevron = true,
                    onClick = { changelogOpen = true },
                )
            }
        }
    }

    MochiSheet(
        sheetState,
        onDismiss = { changelogOpen = false },
    ) {
        if (changelogOpen) {
            ChangelogSheet(sheetState)
        }
    }
}
