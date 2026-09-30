package com.focus.launcher.ui.home

import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.launcher.data.EdgePill
import com.focus.launcher.data.PillEdge
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.monochrome
import com.focus.launcher.ui.theme.LocalFocusColors

@Composable
internal fun EdgePills(
    pills: List<EdgePill>,
    label: (EdgePill) -> String = { it.name },
    onTap: (EdgePill) -> Unit,
    onLongPress: (EdgePill) -> Unit,
    onSwipeIn: (EdgePill) -> Unit,
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        pills.filter { it.enabled }.forEach { pill ->
            val width = pill.widthDp.dp
            val visible = (width * pill.visiblePercent / 100f).coerceAtLeast(56.dp)
            val offsetX = width - visible
            val y = ((maxHeight - 96.dp) * pill.verticalPosition / 100f) + 48.dp - 22.dp
            val colors = LocalFocusColors.current
            val shape = if (pill.edge == PillEdge.LEFT) {
                RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
            } else {
                RoundedCornerShape(topStart = 20.dp, bottomStart = 20.dp)
            }
            Box(
                Modifier
                    .align(if (pill.edge == PillEdge.LEFT) Alignment.TopStart else Alignment.TopEnd)
                    .offset(x = if (pill.edge == PillEdge.LEFT) -offsetX else offsetX, y = y)
                    .width(width)
                    .height(44.dp)
                    .border(1.dp, colors.faint, shape)
                    .pointerInput(pill) {
                        var total = 0f
                        var fired = false
                        detectHorizontalDragGestures(onDragStart = { total = 0f; fired = false }, onHorizontalDrag = { _, amount ->
                            total += amount
                            if (!fired && ((pill.edge == PillEdge.LEFT && total > 24.dp.toPx()) || (pill.edge == PillEdge.RIGHT && total < -24.dp.toPx()))) {
                                fired = true
                                onSwipeIn(pill)
                            }
                        })
                    }
                    .pointerInput(pill) { detectTapGestures(onTap = { onTap(pill) }, onLongPress = { onLongPress(pill) }) },
                contentAlignment = if (pill.edge == PillEdge.LEFT) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                T(
                    (if (pill.icon.isBlank()) label(pill) else "${pill.icon} ${label(pill)}").uppercase(),
                    modifier = Modifier.width(visible).monochrome(),
                    size = 11.sp, color = colors.fg, maxLines = 1, align = TextAlign.Center,
                )
            }
        }
    }
}
