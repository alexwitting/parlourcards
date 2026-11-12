package com.cardgames

import android.content.Context
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.domain.repository.SettingsRepository
import com.cardgames.core.domain.theme.ThemeRepository
import com.cardgames.core.domain.theme.resolve
import com.cardgames.core.ui.cards.AssetCardImages
import com.cardgames.core.ui.cards.CardTableTheme
import com.cardgames.core.ui.cards.PlaceholderCardTableTheme
import com.cardgames.core.ui.cards.toCardTableTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

/**
 * Resolves the active [CardTableTheme] for the whole app: the stored theme id if it names a
 * valid pack, else the default pack, else any pack, else the dev placeholder. Also surfaces the
 * live [settings] so the app shell can gate sound/haptics feedback.
 */
@HiltViewModel
class MainViewModel
    @Inject
    constructor(
        @param:ApplicationContext private val context: Context,
        settingsRepository: SettingsRepository,
        themeRepository: ThemeRepository,
    ) : ViewModel() {
        // Declared before the flows below: they start collecting eagerly during construction, and a
        // settings flow that emits at once would otherwise reach the cache before it's built.
        private val imagesCache = mutableMapOf<String, AssetCardImages>()

        val settings: StateFlow<Settings> =
            settingsRepository
                .observeSettings()
                .catch { Log.e(TAG, "Settings stream failed; using defaults", it) }
                .stateIn(viewModelScope, SharingStarted.Eagerly, Settings())

        val theme: StateFlow<CardTableTheme> =
            settingsRepository
                .observeSettings()
                .distinctUntilChanged { old, new -> old.themeId == new.themeId }
                .map { settings ->
                    val pack = themeRepository.availablePacks().resolve(settings.themeId)
                    if (pack == null) {
                        PlaceholderCardTableTheme
                    } else {
                        // Decode off the main thread before exposing the theme; until then the
                        // previous theme (initially the placeholder) keeps rendering.
                        val images = imagesCache.getOrPut(pack.id) { AssetCardImages(context, pack) }
                        images.preloadAll()
                        pack.toCardTableTheme(images)
                    }
                }.catch {
                    Log.e(TAG, "Theme resolution failed; using the placeholder", it)
                    emit(PlaceholderCardTableTheme)
                }.stateIn(viewModelScope, SharingStarted.Eagerly, PlaceholderCardTableTheme)

        /**
         * Becomes true once a real theme pack (with its bitmaps) has finished preloading. Used
         * by the activity to hold the system splash screen.
         */
        val isReady: StateFlow<Boolean> =
            theme
                .map { it.images != null }
                .stateIn(viewModelScope, SharingStarted.Eagerly, false)

        private companion object {
            const val TAG = "MainViewModel"
        }
    }
