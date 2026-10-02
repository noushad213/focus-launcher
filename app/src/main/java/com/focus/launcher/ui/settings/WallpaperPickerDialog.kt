package com.focus.launcher.ui.settings

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.focus.launcher.ui.components.FocusDialog
import com.focus.launcher.ui.components.MenuRow
import com.focus.launcher.ui.components.T
import com.focus.launcher.ui.home.HomeWallpapers
import com.focus.launcher.ui.theme.LocalFocusColors
import com.focus.launcher.data.ImportedWallpaper
import com.focus.launcher.data.Settings
import com.focus.launcher.data.WallpaperStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun WallpaperPickerDialog(
    settings: Settings,
    onDismiss: () -> Unit,
    onSelect: (String) -> Unit,
    onImport: (ImportedWallpaper) -> Unit,
) {
    val c = LocalFocusColors.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var importing by remember { mutableStateOf(false) }
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            importing = true
            try {
                onImport(WallpaperStorage.import(context, uri))
                onDismiss()
            } catch (_: Exception) {
                Toast.makeText(context, "Could not import that image", Toast.LENGTH_SHORT).show()
            } finally {
                importing = false
            }
        }
    }
    FocusDialog(onDismiss, title = "Home wallpaper", subtitle = "Choose a background for the home screen.", tall = true) {
        LazyColumn {
            item {
                MenuRow(if (importing) "Importing…" else "Add from phone…") {
                    if (!importing) pickImage.launch("image/*")
                }
            }
            item {
                MenuRow("None — use theme", selected = settings.wallpaperId.isEmpty()) {
                    onSelect("")
                    onDismiss()
                }
            }
            items(settings.importedWallpapers, key = { "custom:${it.id}" }) { wallpaper ->
                val bitmap by produceState<ImageBitmap?>(null, wallpaper.id) {
                    value = withContext(Dispatchers.IO) {
                        BitmapFactory.decodeFile(
                            WallpaperStorage.file(context, wallpaper.id).path,
                            BitmapFactory.Options().apply { inSampleSize = 8 },
                        )?.asImageBitmap()
                    }
                }
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onSelect("custom:${wallpaper.id}")
                        onDismiss()
                    }.padding(horizontal = 24.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (bitmap != null) Image(
                        bitmap = bitmap!!,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.width(52.dp).height(86.dp).border(1.dp, c.line),
                    )
                    Column(Modifier.weight(1f)) {
                        T(wallpaper.name, size = 16.sp)
                        if (settings.wallpaperId == "custom:${wallpaper.id}") T("Selected", size = 12.sp, color = c.dim)
                    }
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
                        if (settings.wallpaperId == wallpaper.id) T("Selected", size = 12.sp, color = c.dim)
                    }
                }
            }
        }
    }
}
