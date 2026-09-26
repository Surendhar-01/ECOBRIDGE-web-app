package com.example.i18n

import androidx.annotation.StringRes
import com.example.R
import com.example.model.RoleType

/** Resource ids shared by every screen that renders a role card, label or hint. */
@StringRes
fun RoleType.titleRes(): Int = when (this) {
    RoleType.INFORMAL_COLLECTOR -> R.string.role_collector_title
    RoleType.FORMAL_RECYCLER -> R.string.role_recycler_title
    RoleType.GOVERNMENT_ADMIN -> R.string.role_admin_title
}

@StringRes
fun RoleType.descriptionRes(): Int = when (this) {
    RoleType.INFORMAL_COLLECTOR -> R.string.role_collector_description
    RoleType.FORMAL_RECYCLER -> R.string.role_recycler_description
    RoleType.GOVERNMENT_ADMIN -> R.string.role_admin_description
}

@StringRes
fun RoleType.voiceExplanationRes(): Int = when (this) {
    RoleType.INFORMAL_COLLECTOR -> R.string.role_collector_voice
    RoleType.FORMAL_RECYCLER -> R.string.role_recycler_voice
    RoleType.GOVERNMENT_ADMIN -> R.string.role_admin_voice
}

/** Spoken/rendered explanation of what this portal is for, shown on the sign-in screen. */
@StringRes
fun RoleType.instructionsRes(): Int = when (this) {
    RoleType.INFORMAL_COLLECTOR -> R.string.auth_instructions_collector
    RoleType.FORMAL_RECYCLER -> R.string.auth_instructions_recycler
    RoleType.GOVERNMENT_ADMIN -> R.string.auth_instructions_admin
}
