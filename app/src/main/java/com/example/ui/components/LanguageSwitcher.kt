package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.i18n.AppLanguage
import com.example.i18n.LocalAppLanguage
import com.example.model.Language

/**
 * Compact language control shown in every screen header.
 *
 * Changing the selection writes to [AppLanguage] immediately, which re-resolves every
 * `stringResource` in the tree, so the whole interface switches without a restart.
 */
@Composable
fun LanguageSwitcher(
    modifier: Modifier = Modifier,
    showLabel: Boolean = true,
    onLanguageSelected: (Language) -> Unit = {},
) {
    val context = LocalContext.current
    val current = LocalAppLanguage.current
    var expanded by remember { mutableStateOf(false) }
    val changeLanguageLabel = stringResource(R.string.action_change_language)

    Row(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable { expanded = true }
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .semantics { contentDescription = changeLanguageLabel },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
        if (showLabel) {
            Text(
                text = stringResource(current.labelRes),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 14.sp,
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            Language.entries.forEach { option ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                text = stringResource(option.labelRes),
                                fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal,
                            )
                            Text(
                                text = stringResource(option.englishNameRes),
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                    leadingIcon = { Icon(Icons.Filled.Language, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    trailingIcon = {
                        if (option == current) Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                    },
                    onClick = {
                        expanded = false
                        AppLanguage.set(context, option)
                        onLanguageSelected(option)
                    },
                )
            }
        }
    }
}

/** Full-width list of languages, used inside settings sheets. */
@Composable
fun LanguageOptionList(
    modifier: Modifier = Modifier,
    onLanguageSelected: (Language) -> Unit = {},
) {
    val context = LocalContext.current
    val current = LocalAppLanguage.current

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
    ) {
        Column(modifier = Modifier.padding(vertical = 6.dp)) {
            Language.entries.forEach { option ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            AppLanguage.set(context, option)
                            onLanguageSelected(option)
                        }
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Language,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(option.labelRes),
                            fontWeight = if (option == current) FontWeight.Bold else FontWeight.Normal,
                        )
                        Text(
                            text = stringResource(option.englishNameRes),
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (option == current) {
                        Icon(Icons.Filled.Check, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                    }
                }
            }
        }
    }
}

/** Vertical space helper kept next to the switcher for consistent sheet spacing. */
@Composable
fun LanguageListSpacer() {
    Spacer(modifier = Modifier.height(12.dp))
}

@Composable
fun LanguageListHeader() {
    Text(
        text = stringResource(R.string.settings_app_language),
        style = MaterialTheme.typography.titleSmall,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp),
    )
    Spacer(modifier = Modifier.height(8.dp))
}
