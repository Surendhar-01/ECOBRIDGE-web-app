package com.example.model

import androidx.annotation.StringRes
import com.example.R

enum class RoleType(val id: String) {
    INFORMAL_COLLECTOR("informal_collector"),
    FORMAL_RECYCLER("formal_recycler"),
    GOVERNMENT_ADMIN("government_admin")
}

enum class Language(
    val displayName: String,
    val code: String,
    val localeTag: String,
    val nativeScript: String,
    /** Endonym shown in the language picker; deliberately not translated. */
    @StringRes val labelRes: Int,
    @StringRes val englishNameRes: Int
) {
    ENGLISH("English", "en", "en-IN", "English", R.string.language_english, R.string.language_english_name),
    HINDI("हिंदी", "hi", "hi-IN", "हिन्दी", R.string.language_hindi, R.string.language_hindi_name),
    MARATHI("मराठी", "mr", "mr-IN", "मराठी", R.string.language_marathi, R.string.language_marathi_name)
}

enum class VoiceState {
    IDLE,
    LISTENING,
    PROCESSING,
    RESULT,
    CONFIRMATION,
    ERROR
}

enum class VoiceSpeed(val rate: Float) {
    SLOW(0.75f),
    NORMAL(1.0f)
}

data class VoiceSettings(
    val guidanceEnabled: Boolean = true,
    val speechSpeed: VoiceSpeed = VoiceSpeed.NORMAL,
    val isMuted: Boolean = false
)

enum class VoiceIntentType {
    ROLE_SELECTION,
    VOICE_HELP,
    CHANGE_LANGUAGE,
    REPEAT_INSTRUCTION,
    UNKNOWN
}

data class VoiceIntentResult(
    val intent: VoiceIntentType,
    val detectedRole: RoleType? = null,
    val targetLanguage: Language? = null,
    val transcript: String = "",
    val confidence: Float = 0.92f,
    val requiresConfirmation: Boolean = true
)
