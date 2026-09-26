package com.example.i18n

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import com.example.model.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Single source of truth for the active interface language.
 *
 * The choice is persisted so it survives process death, and it is published as a
 * [StateFlow] so every screen recomposes the moment the user switches language.
 */
object AppLanguage {

    private const val PREFS_NAME = "ewaste_prefs"
    private const val PREF_KEY = "selected_lang"

    private val _language = MutableStateFlow(Language.ENGLISH)
    val language: StateFlow<Language> = _language.asStateFlow()

    /** Reads the persisted choice on first access, falling back to the device locale. */
    fun initialize(context: Context) {
        val stored = runCatching {
            context.applicationContext
                .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .getString(PREF_KEY, null)
        }.getOrNull()
        _language.value = when (stored) {
            null -> fromSystemLocale(context)
            else -> Language.entries.firstOrNull { it.code == stored } ?: Language.ENGLISH
        }
        applyLocale(context, _language.value)
    }

    fun set(context: Context, language: Language) {
        if (_language.value == language) return
        _language.value = language
        context.applicationContext
            .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(PREF_KEY, language.code)
            .apply()
        applyLocale(context, language)
    }

    fun fromSystemLocale(context: Context): Language {
        val tag = context.resources.configuration.locales[0]?.language.orEmpty()
        return Language.entries.firstOrNull { it.code == tag } ?: Language.ENGLISH
    }

    /**
     * Android reads the default locale for `DateFormat`, speech synthesis and any
     * framework formatting done outside of a `Context` we control.
     */
    private fun applyLocale(context: Context, language: Language) {
        Locale.setDefault(Locale.forLanguageTag(language.localeTag))
    }

    /** Returns a context whose resources resolve [language], for non-Compose callers. */
    fun localizedContext(base: Context, language: Language): Context {
        val configuration = Configuration(base.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language.localeTag))
        return base.createConfigurationContext(configuration)
    }
}

val LocalAppLanguage = staticCompositionLocalOf { Language.ENGLISH }

/**
 * Swaps the configuration seen by the whole Compose tree so every `stringResource`
 * call, and any `LocalContext` consumer, resolves in [language] immediately.
 *
 * Overriding the composition locals avoids an activity recreation, which would
 * otherwise discard in-progress screen state such as a half-filled lot wizard.
 */
@Composable
fun ProvideAppLanguage(language: Language, content: @Composable () -> Unit) {
    val base = LocalContext.current
    val localizedContext = remember(base, language) { AppLanguage.localizedContext(base, language) }
    val configuration = remember(localizedContext) { localizedContext.resources.configuration }

    LaunchedEffect(language) { Locale.setDefault(Locale.forLanguageTag(language.localeTag)) }

    CompositionLocalProvider(
        LocalAppLanguage provides language,
        LocalContext provides localizedContext,
        LocalConfiguration provides configuration,
    ) {
        content()
    }
}
