package com.banbiao.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import ui.feice.mochi.list.MochiListStatusChip
import ui.feice.mochi.theme.MochiTheme

/** 更新日志和更新弹窗共用的「更新 / 修复」胶囊。 */
@Composable
internal fun CadenceNoteChip(
    label: String,
    accent: Boolean,
    modifier: Modifier = Modifier,
) {
    val scheme = MochiTheme.colorScheme
    MochiListStatusChip(
        text = label,
        modifier = modifier,
        containerColor = if (accent) {
            scheme.primary.copy(alpha = 0.14f)
        } else {
            scheme.onSurfaceVariant.copy(alpha = 0.10f)
        },
        contentColor = if (accent) scheme.primary else scheme.onSurfaceVariant,
    )
}
