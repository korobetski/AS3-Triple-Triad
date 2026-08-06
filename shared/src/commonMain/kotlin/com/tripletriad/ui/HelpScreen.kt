package com.tripletriad.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave

const val HELP_LIST_TEST_TAG: String = "help-list"

/** `help-rule-<constant>`, by the AS3 rule constant — which is also the label's i18n key. */
fun helpRuleTestTag(ruleKey: String): String = "help-rule-$ruleKey"

/** `help-text-<constant>`, present only while that rule is the expanded one. */
fun helpTextTestTag(ruleKey: String): String = "help-text-$ruleKey"

/**
 * The rules, explained — `HelpScreen.as`.
 *
 * Tap a rule to open its text, tap it again to close. The original was a master/detail pair, a list
 * on the left and a text pane on the right; an accordion is the same information on a phone and
 * needs no second column.
 *
 * ### The list is not [com.tripletriad.model.RuleKeys], deliberately
 *
 * `RULE_COMBO` is in this list and in no other: it is a **dead constant** everywhere else — nothing
 * writes or reads a combo flag, and combo fires unconditionally whenever Same, Same Wall or Plus
 * captures (see [com.tripletriad.model.GameRules.comboEnabled]). The help screen is the one place
 * it legitimately appears, because it describes something the game really does. So this file keeps
 * its own order, transcribed from `HelpScreen.as:72-89`, rather than deriving one from the rules
 * engine's table — which correctly excludes combo and would therefore omit it.
 *
 * `HelpScreen.as:77` and `:84` both list `RULE_CHAOS`, so the original's list is eighteen entries
 * with one shown twice. Deduplicated here; a duplicated row is not behaviour to preserve.
 *
 * ### Three help texts are placeholders in the original data
 *
 * `RULE_SAME_WALL_HELP`, `RULE_COMBO_HELP` and `RULE_ELEMENTAL_HELP` resolve to the rule's own name
 * in all four bundles — "Same Wall", "Combo", "Elemental" — so the original showed the title twice
 * and explained nothing. Shown as-is: the bundles are Square Enix wording imported by
 * `tools/import_locales.py` and writing three paragraphs of our own into them would be inventing
 * source text. The gap is real and it is the translators' to fill, not this screen's to paper over.
 *
 * `RULE_ORDER`'s **label** has the same shape of defect the other way round: it reads `Ordre` in
 * `en_US`, an untranslated French leftover in the imported bundle.
 */
@Composable
internal fun HelpScreen(profile: GameSave, onBack: () -> Unit) {
    val strings = LocalStrings.current
    var open by remember { mutableStateOf<String?>(null) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.HELP], onBack = onBack) {
        LazyColumn(
            modifier = Modifier.testTag(HELP_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            items(HELP_RULES, key = { it }) { ruleKey ->
                HelpRow(
                    ruleKey = ruleKey,
                    isOpen = open == ruleKey,
                    onClick = { open = if (open == ruleKey) null else ruleKey },
                )
            }
        }
    }
}

@Composable
private fun HelpRow(ruleKey: String, isOpen: Boolean, onClick: () -> Unit) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(helpRuleTestTag(ruleKey))
            .fillMaxWidth()
            .rowSurface(selected = isOpen)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = strings[ruleKey],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
        )
        // `AnimatedVisibility` rather than an `if`, so the text slides in instead of the row
        // snapping to twice its height — an accordion that jumps reads as a layout bug.
        AnimatedVisibility(visible = isOpen) {
            Text(
                text = strings["${ruleKey}_HELP"],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.testTag(helpTextTestTag(ruleKey)),
            )
        }
    }
}

/**
 * `HelpScreen.as:72-89`, in order, with the duplicated `RULE_CHAOS` dropped.
 *
 * Public so a test can sweep it: every entry must resolve to both a label and a `_HELP` text in the
 * `en_US` bundle, which is the check that would have caught the three placeholder texts above being
 * *missing* rather than merely thin.
 */
internal val HELP_RULES: List<String> = listOf(
    "RULE_ALL_OPEN",
    "RULE_THREE_OPEN",
    "RULE_SUDDEN_DEATH",
    "RULE_RANDOM",
    "RULE_ORDER",
    "RULE_CHAOS",
    "RULE_REVERSE",
    "RULE_FALLEN_ACE",
    "RULE_SAME",
    "RULE_SAME_WALL",
    "RULE_PLUS",
    "RULE_COMBO",
    "RULE_ASCENSION",
    "RULE_DESCENSION",
    "RULE_ELEMENTAL",
    "RULE_SWAP",
    "RULE_ROULETTE",
)
