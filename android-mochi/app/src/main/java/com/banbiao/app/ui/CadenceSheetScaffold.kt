package com.banbiao.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import ui.feice.mochi.basic.MochiSheetContent
import ui.feice.mochi.basic.MochiSheetHeader
import ui.feice.mochi.effect.mask.mochiScrollEdgeBlur
import ui.feice.mochi.theme.MochiTheme

/**
 * Sheet title sits directly over the body. Its translucent gradient and the
 * body's top-edge blur are always present; they do not wait for a scroll.
 */
@Composable
internal fun CadenceSheetScaffold(
    title: String,
    navigationIcon: (@Composable () -> Unit)? = null,
    actions: @Composable () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit,
) {
    val scheme = MochiTheme.colorScheme

    Box(Modifier.fillMaxWidth()) {
        MochiSheetContent(
            Modifier.mochiScrollEdgeBlur(68.dp) { 1.35f },
        ) {
            Spacer(Modifier.height(68.dp))
            content()
        }

        MochiSheetHeader(
            title = title,
            navigationIcon = navigationIcon,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to scheme.surface.copy(alpha = 0.68f),
                        0.52f to scheme.surface.copy(alpha = 0.42f),
                        1f to Color.Transparent,
                    ),
                )
                .padding(bottom = 12.dp),
            actions = actions,
        )
    }
}
