package com.example.data

import com.example.model.Language

/**
 * Picks the right column for database-sourced content.
 *
 * The Supabase tables store English in the base column and Hindi/Marathi in
 * `_hi` / `_mr` siblings (migration 0008). A blank or absent translation falls
 * back to English so the screen never shows an empty card.
 */
private fun pick(base: String, hi: String?, mr: String?, language: Language): String = when (language) {
    Language.ENGLISH -> base
    Language.HINDI -> hi?.takeIf { it.isNotBlank() } ?: base
    Language.MARATHI -> mr?.takeIf { it.isNotBlank() } ?: base
}

fun SafetyGuidelineEntity.practiceTitle(language: Language): String =
    pick(practiceTitle, practiceTitleHi, practiceTitleMr, language)

fun SafetyGuidelineEntity.whyUnsafe(language: Language): String =
    pick(whyUnsafe, whyUnsafeHi, whyUnsafeMr, language)

fun SafetyGuidelineEntity.whatIsLost(language: Language): String =
    pick(whatIsLost, whatIsLostHi, whatIsLostMr, language)

fun SafetyGuidelineEntity.safeFormalAlternative(language: Language): String =
    pick(safeFormalAlternative, safeFormalAlternativeHi, safeFormalAlternativeMr, language)

fun PriceRecordEntity.subCategory(language: Language): String =
    pick(subCategory, subCategoryHi, subCategoryMr, language)

fun PriceRecordEntity.locationLabel(language: Language): String =
    pick(location, locationHi, locationMr, language)

fun PriceRecordEntity.keyMetals(language: Language): String =
    pick(keyMetalsJoined, keyMetalsJoinedHi, keyMetalsJoinedMr, language)

fun RecyclerEntity.serviceAreaLabel(language: Language): String =
    pick(serviceArea.ifBlank { city }, serviceAreaHi, serviceAreaMr, language)
