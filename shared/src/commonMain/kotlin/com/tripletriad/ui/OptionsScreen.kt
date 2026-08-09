package com.tripletriad.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.settings.UserSettings

const val OPTIONS_BACKGROUND_VOLUME_TEST_TAG: String = "options-background-volume"
const val OPTIONS_NOISE_VOLUME_TEST_TAG: String = "options-noise-volume"

/** `options-language-fr_FR` and so on, so a test can name the chip it means. */
fun optionsLanguageTestTag(locale: AppLocale): String = "options-language-${locale.tag}"

/**
 * The three settings `UserSettings.json` actually holds: language and the two volumes.
 *
 * Grouped under the AS3's own headings — `STR_GENERAL_SETTINGS` and `STR_AUDIO_SETTINGS`, which
 * `SettingsScreen.as` uses for the same split — so the four bundles already carry every label on
 * this screen except **Back** and the audio caveat.
 *
 * ### Changes apply and persist immediately
 *
 * There is no Save button, and `STR_SETTINGS_SAVED` (which exists in all four bundles) is not
 * used. `SettingsScreen.as` had one because Feathers gave it a form; on a phone, a settings pane
 * you can leave with the system Back gesture must not be able to lose what you just did. Picking a
 * language redraws this screen in it, which *is* the confirmation — a toast saying "saved" would
 * be telling the user something the screen already showed them.
 *
 * ### The volumes are honest about doing nothing
 *
 * They persist, and the AS3 file has carried both fields all along, so they are here rather than
 * hidden. But no audio is implemented (Task 1.5), so the caveat under them says so. Silent sliders
 * with no explanation would read as a bug.
 *
 * ### On the shell, like everything else
 *
 * It used to draw its own centred title and its own `‹ Back` text button, which made it the one
 * screen whose back control sat somewhere different from every other screen's. [ScreenScaffold] now
 * provides both, and the two groups are cards rather than headings over bare rows — a settings
 * pane is a list of *groups*, and a group whose edge the eye cannot find is a heading pretending to
 * be one.
 */
@Composable
internal fun OptionsScreen(settings: SettingsHolder, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val current = settings.value

    ScreenScaffold(title = strings[StringKeys.SETTINGS], onBack = onBack) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SettingsGroup(strings[StringKeys.GENERAL_SETTINGS]) {
                Label(strings[StringKeys.LANGUAGE])
                LanguageChoice(current) { locale ->
                    settings.update { it.copy(language = locale.tag) }
                }
            }

            SettingsGroup(strings[StringKeys.AUDIO_SETTINGS]) {
                Text(
                    text = strings[StringKeys.AUDIO_PENDING],
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                    style = MaterialTheme.typography.labelSmall,
                )
                VolumeRow(
                    label = strings[StringKeys.BACKGROUND_VOLUME],
                    value = current.backgroundVolume,
                    tag = OPTIONS_BACKGROUND_VOLUME_TEST_TAG,
                ) { volume -> settings.update { it.copy(backgroundVolume = volume) } }
                VolumeRow(
                    label = strings[StringKeys.NOISE_VOLUME],
                    value = current.noiseVolume,
                    tag = OPTIONS_NOISE_VOLUME_TEST_TAG,
                ) { volume -> settings.update { it.copy(noiseVolume = volume) } }
            }
        }
    }
}

/**
 * A heading and the card under it.
 *
 * The heading stays outside the card: Material puts a group's label above its container, and a
 * label inside one reads as the first row of it.
 */
@Composable
private fun SettingsGroup(heading: String, content: @Composable ColumnScope.() -> Unit) {
    Column(modifier = Modifier.fillMaxWidth()) {
        SectionHeading(heading)
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = MaterialTheme.colorScheme.surfaceVariant,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                content = content,
            )
        }
    }
}

/**
 * One chip per locale, labelled in the language it selects.
 *
 * `AppLocale.displayName` is the endonym — `Deutsch`, not `German` — so the list is readable to
 * someone who has landed in a language they cannot read and is looking for their own.
 */
@Composable
private fun LanguageChoice(settings: UserSettings, onPick: (AppLocale) -> Unit) {
    val selected = settings.locale
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        for (locale in AppLocale.entries) {
            FilterChip(
                selected = locale == selected,
                onClick = { onPick(locale) },
                label = { Text(locale.displayName, style = MaterialTheme.typography.labelLarge) },
                shape = MaterialTheme.shapes.extraSmall,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primary,
                    selectedLabelColor = MaterialTheme.colorScheme.onSurface,
                    labelColor = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                ),
                modifier = Modifier.testTag(optionsLanguageTestTag(locale)),
            )
        }
    }
}

@Composable
private fun VolumeRow(
    label: String,
    value: Float,
    tag: String,
    onChange: (Float) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Label(label)
            // Percent rather than the raw 0..1: `SoundTransform.volume`'s scale is an
            // implementation detail and 0.6 means nothing on a slider.
            Text(
                text = "${(value * PERCENT).toInt()}%",
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelMedium,
            )
        }
        Slider(
            value = value,
            onValueChange = onChange,
            modifier = Modifier.testTag(tag),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.tertiary,
                activeTrackColor = MaterialTheme.colorScheme.tertiary,
                inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f),
            ),
        )
    }
}

@Composable
private fun SectionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        color = MaterialTheme.colorScheme.tertiary,
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        modifier = modifier.padding(bottom = 4.dp),
    )
}

@Composable
private fun Label(text: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val PERCENT = 100
