package com.banbiao.app.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiIconButton
import ui.feice.mochi.basic.Text
import ui.feice.mochi.icons.MochiIonicon
import ui.feice.mochi.theme.MochiTheme
import com.banbiao.app.data.CADENCE_CHANGELOG
import com.banbiao.app.data.ChangelogEntry

@Composable
internal fun ChangelogSheet(
    sheetState: ui.feice.mochi.basic.MochiSheetState,
) {
    CadenceSheetScaffold(
        title = "更新日志",
        actions = {
            MochiIconButton(onClick = { sheetState.hide() }) {
                MochiIonicon("close", size = 20.dp)
            }
        },
    ) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            CADENCE_CHANGELOG.forEachIndexed { index, entry ->
                ChangelogNode(
                    entry = entry,
                    first = index == 0,
                    last = index == CADENCE_CHANGELOG.lastIndex,
                )
            }
        }
    }
}

@Composable
private fun ChangelogNode(
    entry: ChangelogEntry,
    first: Boolean,
    last: Boolean,
) {
    val scheme = MochiTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val nodeX = 10.dp.toPx()
                val branchEnd = 24.dp.toPx()
                val nodeY = 14.dp.toPx()
                val line = 1.dp.toPx()
                val top = if (first) nodeY else 0f
                val bottom = if (last) nodeY else size.height
                if (bottom > top) {
                    drawLine(
                        color = scheme.outlineVariant.copy(alpha = 0.65f),
                        start = Offset(nodeX, top),
                        end = Offset(nodeX, bottom),
                        strokeWidth = line,
                    )
                }
                drawLine(
                    color = scheme.outlineVariant.copy(alpha = 0.65f),
                    start = Offset(nodeX, nodeY),
                    end = Offset(branchEnd, nodeY),
                    strokeWidth = line,
                )
                drawCircle(
                    color = if (entry.current) scheme.primary else scheme.onSurfaceVariant.copy(alpha = 0.55f),
                    radius = 3.5.dp.toPx(),
                    center = Offset(nodeX, nodeY),
                )
            }
            .padding(start = 32.dp, top = 4.dp, bottom = 14.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    entry.version,
                    style = MochiTheme.textStyles.body.copy(fontWeight = FontWeight.SemiBold),
                    color = scheme.onSurface,
                )
                if (entry.current) {
                    Spacer(Modifier.width(7.dp))
                    Text(
                        "当前",
                        style = MochiTheme.textStyles.labelSmall,
                        color = scheme.primary,
                    )
                }
            }
            entry.updates.forEach { line ->
                ChangelogLine("更新", line, accent = true)
            }
            entry.fixes.forEach { line ->
                ChangelogLine("修复", line, accent = false)
            }
        }
    }
}

@Composable
private fun ChangelogLine(label: String, line: String, accent: Boolean) {
    Row(
        Modifier.fillMaxWidth().padding(top = 6.dp),
        verticalAlignment = Alignment.Top,
    ) {
        CadenceNoteChip(label = label, accent = accent)
        Spacer(Modifier.width(8.dp))
        Text(
            line,
            modifier = Modifier.weight(1f).padding(top = 2.dp),
            style = MochiTheme.textStyles.footnote,
            color = MochiTheme.colorScheme.onSurfaceVariant,
        )
    }
}
