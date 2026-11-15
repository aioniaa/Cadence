package com.banbiao.app.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.banbiao.app.core.UpdateManager
import ui.feice.mochi.basic.Text
import ui.feice.mochi.theme.MochiTheme

@Composable
internal fun UpdateNoteList(notes: List<UpdateManager.Update.Note>) {
    Column(
        Modifier
            .fillMaxWidth()
            .heightIn(max = 360.dp)
            .verticalScroll(rememberScrollState())
            .padding(top = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        notes.forEach { note ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                CadenceNoteChip(
                    label = if (note.fix) "修复" else "更新",
                    accent = !note.fix,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    note.text,
                    modifier = Modifier.weight(1f).padding(top = 2.dp),
                    style = MochiTheme.textStyles.footnote,
                    color = MochiTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
