package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.settings.UserSettings

const val OPTIONS_BACK_TEST_TAG: String = "options-back"
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
 */
@Composable
internal fun OptionsScreen(settings: SettingsHolder, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val current = settings.value

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = strings[StringKeys.SETTINGS],
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 20.dp),
        )

        Column(
            modifier = Modifier.widthIn(max = PaneMaxWidth).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            SectionHeading(strings[StringKeys.GENERAL_SETTINGS])
            Label(strings[StringKeys.LANGUAGE])
            LanguageChoice(current) { locale ->
                settings.update { it.copy(language = locale.tag) }
            }

            SectionHeading(
                text = strings[StringKeys.AUDIO_SETTINGS],
                modifier = Modifier.padding(top = 20.dp),
            )
            Text(
                text = strings[StringKeys.AUDIO_PENDING],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f),
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

            TextButton(
                onClick = onBack,
                modifier = Modifier.padding(top = 24.dp).testTag(OPTIONS_BACK_TEST_TAG),
            ) {
                Text(
                    text = "‹ ${strings[StringKeys.BACK]}",
                    color = MaterialTheme.colorScheme.tertiary,
                    style = MaterialTheme.typography.titleSmall,
                )
            }
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
                label = { Text(locale.displayName, fontSize = 13.sp) },
                shape = RoundedCornerShape(4.dp),
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
    Column(modifier = Modifier.padding(top = 8.dp)) {
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
    Text(text = text, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp)
}

private val PaneMaxWidth = 420.dp
private const val PERCENT = 100
