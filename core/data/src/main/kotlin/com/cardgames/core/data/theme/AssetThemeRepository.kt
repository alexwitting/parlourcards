package com.cardgames.core.data.theme

import android.content.Context
import android.graphics.BitmapFactory
import android.util.Log
import com.cardgames.core.domain.theme.ThemePack
import com.cardgames.core.domain.theme.ThemeRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/** [ThemeRepository] over the APK's `assets/themes/` folder. Packs never change at runtime, so one scan is cached. */
@Singleton
internal class AssetThemeRepository
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
    ) : ThemeRepository {
        private val packs: List<ThemePack> by lazy {
            ThemePackLoader(AssetManagerThemeSource(context)).loadValidPacks()
        }

        override suspend fun availablePacks(): List<ThemePack> = withContext(Dispatchers.IO) { packs }
    }

private class AssetManagerThemeSource(
    private val context: Context,
) : ThemeAssetSource {
    private val assets = context.assets

    override fun listPackIds(): List<String> = list("")

    override fun list(
        packId: String,
        subPath: String,
    ): List<String> {
        val path = listOf("themes", packId, subPath).filter { it.isNotEmpty() }.joinToString("/")
        return runCatching { assets.list(path)?.toList() }
            .onFailure { Log.e(TAG, "Couldn't list assets at $path", it) }
            .getOrNull()
            .orEmpty()
    }

    override fun readText(path: String): String? = runCatching { assets.open(path).bufferedReader().use { it.readText() } }.getOrNull()

    override fun imageSize(path: String): Pair<Int, Int>? =
        runCatching {
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            assets.open(path).use { BitmapFactory.decodeStream(it, null, options) }
            (options.outWidth to options.outHeight).takeIf { options.outWidth > 0 && options.outHeight > 0 }
        }.getOrNull()

    private companion object {
        const val TAG = "AssetThemeSource"
    }
}
