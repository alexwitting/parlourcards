package com.cardgames

import android.content.Context
import com.cardgames.core.domain.repository.Settings
import com.cardgames.core.testing.FakeSettingsRepository
import com.cardgames.core.testing.FakeThemeRepository
import com.cardgames.core.testing.MainDispatcherExtension
import com.cardgames.core.ui.cards.PlaceholderCardTableTheme
import io.mockk.mockk
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension

class MainViewModelTest {
    @JvmField
    @RegisterExtension
    val mainDispatcherExtension = MainDispatcherExtension()

    private val settings = FakeSettingsRepository()
    private val themes = FakeThemeRepository()

    private fun viewModel() = MainViewModel(mockk<Context>(relaxed = true), settings, themes)

    @Test
    fun `the live settings are surfaced as they change`() {
        val vm = viewModel()
        assertEquals(Settings(), vm.settings.value)

        settings.settings = settings.settings.copy(soundEnabled = false)

        assertFalse(vm.settings.value.soundEnabled)
    }

    @Test
    fun `with no packs installed the placeholder theme shows and the app isn't held as loading`() {
        // The fake settings flow emits at once, during construction, before the images cache exists.
        val vm = viewModel()

        assertSame(PlaceholderCardTableTheme, vm.theme.value)
        assertFalse(vm.isReady.value)
    }
}
