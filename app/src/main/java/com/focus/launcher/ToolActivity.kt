package com.focus.launcher

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.focus.launcher.data.Settings
import com.focus.launcher.data.TodoItem
import com.focus.launcher.ui.components.Hairline
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.components.TextInputDialog
import com.focus.launcher.ui.components.focusTextStyle
import com.focus.launcher.ui.theme.FocusTheme
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.ui.theme.applyFocusWindow
import java.util.UUID

/** Small first-party tools opened by gestures and pills. Their data stays in [Settings]. */
class ToolActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        applyFocusWindow(Graph.settings.value.dark)
        val tool = intent.getStringExtra(EXTRA_TOOL) ?: TODO
        setContent {
            val settings by Graph.settings.flow.collectAsStateWithLifecycle()
            LaunchedEffect(settings.dark) { applyFocusWindow(settings.dark) }
            FocusTheme(settings) {
                when (tool) {
                    NOTE -> NoteTool(settings, ::finish)
                    else -> TodoTool(settings, ::finish)
                }
            }
        }
    }

    companion object {
        const val TODO = "todo"
        const val NOTE = "note"
        private const val EXTRA_TOOL = "tool"
        fun intent(context: Context, tool: String) =
            Intent(context, ToolActivity::class.java).putExtra(EXTRA_TOOL, tool)
    }
}

@Composable
private fun ToolPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.fillMaxSize().systemBarsPadding().imePadding()) {
        Row(
            Modifier.fillMaxWidth().padding(start = 12.dp, end = 24.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            T("←", Modifier.clickable(onClick = onBack).padding(12.dp), size = 22.sp)
            T(title, Modifier.weight(1f), size = 22.sp, weight = FontWeight.Medium)
        }
        Hairline()
        content()
    }
}

@Composable
private fun TodoTool(settings: Settings, onBack: () -> Unit) {
    var adding by remember { mutableStateOf(false) }
    ToolPage("Todo", onBack) {
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState())) {
            settings.todos.forEach { item ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        Graph.settings.update { current ->
                            current.copy(todos = current.todos.map { if (it.id == item.id) it.copy(done = !it.done) else it })
                        }
                    }.padding(horizontal = 24.dp, vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(if (item.done) "✓" else "○", Modifier.padding(end = 16.dp), size = 18.sp)
                    T(item.text, Modifier.weight(1f), size = 16.sp,
                        color = if (item.done) LocalFocusColors.current.dim else LocalFocusColors.current.fg,
                        decoration = if (item.done) TextDecoration.LineThrough else null)
                    T("×", Modifier.clickable {
                        Graph.settings.update { it.copy(todos = it.todos.filterNot { todo -> todo.id == item.id }) }
                    }.padding(10.dp), size = 18.sp, color = LocalFocusColors.current.dim)
                }
                Hairline(Modifier.padding(horizontal = 24.dp))
            }
            if (settings.todos.isEmpty()) {
                T("No tasks", Modifier.padding(horizontal = 24.dp, vertical = 28.dp), color = LocalFocusColors.current.dim)
            }
            T("+  Add task", Modifier.fillMaxWidth().clickable { adding = true }.padding(horizontal = 24.dp, vertical = 20.dp), size = 16.sp)
            if (settings.todos.any { it.done }) {
                T("Clear completed", Modifier.fillMaxWidth().clickable {
                    Graph.settings.update { it.copy(todos = it.todos.filterNot(TodoItem::done)) }
                }.padding(horizontal = 24.dp, vertical = 16.dp), size = 14.sp, color = LocalFocusColors.current.dim)
            }
        }
    }
    if (adding) TextInputDialog("New task", "", "What needs doing?", { adding = false }) { text ->
        val clean = text.trim().take(160)
        if (clean.isNotEmpty()) Graph.settings.update { it.copy(todos = it.todos + TodoItem(UUID.randomUUID().toString(), clean)) }
        adding = false
    }
}

@Composable
private fun NoteTool(settings: Settings, onBack: () -> Unit) {
    var editing by remember { mutableStateOf(false) }
    var draft by remember(settings.note, editing) { mutableStateOf(settings.note) }
    ToolPage("Note", onBack) {
        Column(Modifier.weight(1f).fillMaxWidth().padding(24.dp)) {
            if (editing) {
                BasicTextField(
                    value = draft,
                    onValueChange = { draft = it.take(10_000) },
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                    textStyle = focusTextStyle(17.sp, lineHeight = 25.sp),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(LocalFocusColors.current.fg),
                )
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    T("Cancel", Modifier.clickable { editing = false }.padding(top = 14.dp, bottom = 14.dp, end = 20.dp), color = LocalFocusColors.current.dim)
                    Spacer(Modifier.weight(1f))
                    T("Save", Modifier.clickable { Graph.settings.update { it.copy(note = draft) }; editing = false }.padding(vertical = 14.dp), weight = FontWeight.Medium)
                }
            } else {
                T(settings.note.ifBlank { "No note yet" }, Modifier.weight(1f).fillMaxWidth(), size = 17.sp,
                    color = if (settings.note.isBlank()) LocalFocusColors.current.dim else LocalFocusColors.current.fg,
                    lineHeight = 25.sp)
                T("✎  Edit", Modifier.clickable { editing = true }.padding(vertical = 16.dp), size = 15.sp)
            }
        }
    }
}
