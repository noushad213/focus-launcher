package com.focus.launcher.ui.home

import androidx.annotation.DrawableRes
import com.focus.launcher.R
import com.focus.launcher.data.ImportedWallpaper
import com.focus.launcher.data.Settings

data class HomeWallpaper(
    val id: String,
    val name: String,
    @param:DrawableRes val image: Int,
    /** True when the top edge needs light system-bar icons. */
    val darkTop: Boolean,
)

object HomeWallpapers {
    val all = listOf(
        HomeWallpaper("mountain_forest", "Mountain forest", R.drawable.wallpaper_mountain_forest, false),
        HomeWallpaper("mountain_village", "Mountain village", R.drawable.wallpaper_mountain_village, false),
        HomeWallpaper("ink_lake", "Ink lake", R.drawable.wallpaper_ink_lake, false),
        HomeWallpaper("abstract_hills", "Abstract hills", R.drawable.wallpaper_abstract_hills, false),
        HomeWallpaper("red_branch", "Red branch", R.drawable.wallpaper_red_branch, true),
        HomeWallpaper("red_vine", "Red vine", R.drawable.wallpaper_red_vine, true),
        HomeWallpaper("portrait_motion", "Portrait in motion", R.drawable.wallpaper_portrait_motion, false),
        HomeWallpaper("butterfly_portrait", "Butterfly portrait", R.drawable.wallpaper_butterfly_portrait, false),
        HomeWallpaper("white_figure", "White figure", R.drawable.wallpaper_white_figure, true),
    )

    fun find(id: String): HomeWallpaper? = all.firstOrNull { it.id == id }

    fun imported(settings: Settings): ImportedWallpaper? = settings.importedWallpapers.firstOrNull {
        settings.wallpaperId == "custom:${it.id}"
    }

    fun hasSelection(settings: Settings): Boolean = find(settings.wallpaperId) != null || imported(settings) != null

    fun darkTop(settings: Settings): Boolean = find(settings.wallpaperId)?.darkTop
        ?: imported(settings)?.darkTop ?: settings.dark
}
