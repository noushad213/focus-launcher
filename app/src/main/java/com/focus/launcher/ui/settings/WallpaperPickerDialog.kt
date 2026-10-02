package com.focus.launcher.ui.settings

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.MenuRow
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.home.HomeWallpapers
import com.focus.launcher.ui.theme.LocalFocusColors

@Composable
internal fun WallpaperPickerDialog(selectedId: String, onDismiss: () -> Unit, onSelect: (String) -> Unit) {
    val c = LocalFocusColors.current
    FocusDialog(onDismiss, title = "Home wallpaper", subtitle = "Choose a background for the home screen.", tall = true) {
        LazyColumn {
            item {
                MenuRow("None — use theme", selected = selectedId.isEmpty()) {
                    onSelect("")
                    onDismiss()
                }
            }
            items(HomeWallpapers.all, key = { it.id }) { wallpaper ->
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onSelect(wallpaper.id)
                        onDismiss()
                    }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Image(
                        painter = painterResource(wallpaper.image),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(52.dp).height(86.dp).border(1.dp, c.line),
                    )
                    Column(Modifier.weight(1f)) {
                        T(wallpaper.name, size = 16.sp)
                        if (selectedId == wallpaper.id) T("Selected", size = 12.sp, color = c.dim)
                    }
                }
            }
        }
    }
}
