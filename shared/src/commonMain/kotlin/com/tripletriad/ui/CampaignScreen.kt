package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.data.Campaign
import com.tripletriad.data.CampaignStep
import com.tripletriad.data.CardCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.time.Clock
import com.tripletriad.ui.theme.LocalTtoColors

/** The ladder's entry screen — its opponent list and the Start that charges the fee. */
const val CAMPAIGN_LIST_TEST_TAG: String = "campaign-list"

/** Start. Disabled when the character cannot afford the entry fee. */
const val CAMPAIGN_START_TEST_TAG: String = "campaign-start"

/** How far up the ladder the player is, shown while one is being played. */
const val CAMPAIGN_STEP_TEST_TAG: String = "campaign-step"

/** `campaign-row-<key>` — the entry into one ladder, on the opponent list. */
fun campaignRowTestTag(key: String): String = "campaign-row-$key"

/**
 * A tournament ladder's entry screen — `CCGroupScreen` and `GSGroupScreen`, which are one screen.
 *
 * The two AS3 files are the same 121 lines with a different title and a different list of names, so
 * they are one composable over [Campaign] here. It shows what the player is paying for — the
 * opponents, in order, with the rules each imposes — and a Start disabled below the fee, exactly as
 * `startCampaign.isEnabled = (Game.PROFILE_DATAS.MGP >= 500)`.
 *
 * ### The fee is the only fee in the game
 *
 * `NPC.matchFee` is declared for all 85 opponents and **never charged** — see
 * [com.tripletriad.data.MatchRewards]. This 500 is: `Game.PROFILE_DATAS.MGP -= 500` in both entry
 * screens. So a ladder is the one thing in Triple Triad Online that costs money to attempt, which
 * is what makes losing the last rung mean something — a defeat sends the player back to the first
 * ([Campaign.nextStep]) and a second attempt is another 500.
 *
 * @param onStart charge taken, ladder open. The caller navigates; this decides *whether*.
 */
@Composable
internal fun CampaignScreen(
    campaign: Campaign,
    profile: GameSave,
    onStart: () -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current

    ScreenScaffold(title = campaignTitle(strings, campaign), onBack = onBack) {
        Column(
            modifier = Modifier.testTag(CAMPAIGN_LIST_TEST_TAG).fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            for ((step, entry) in campaign.steps.withIndex()) {
                RungRow(step = step, entry = entry)
            }
        }

        Text(
            text = "${strings[StringKeys.MATCH_FEE]} ${campaign.fee} ${strings[StringKeys.MGP]}",
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(vertical = 12.dp),
        )

        WideButton(
            label = strings[StringKeys.START],
            tag = CAMPAIGN_START_TEST_TAG,
            // `isEnabled = (MGP >= 500)`. Disabled rather than hidden, so the reason a ladder
            // cannot be entered is visible next to its price.
            enabled = profile.mgp >= campaign.fee,
            onClick = onStart,
        )
    }
}

/**
 * One rung: its number, who it is, and what they impose.
 *
 * Numbered, which the original's list is not — its `GroupedList` is seven labels and an icon. The
 * order is the whole point of a ladder, and a bare list of names does not say that the seventh is
 * played after the sixth.
 */
@Composable
private fun RungRow(step: Int, entry: CampaignStep) {
    val strings = LocalStrings.current
    val npc = entry.npc

    Row(
        modifier = Modifier.fillMaxWidth().rowSurface().padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "${step + 1}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.Bold,
        )
        // The Card Club's seven rungs are the ones with no portrait in the asset tree, so this is
        // also where the monogram fallback earns its keep — see `NpcPortrait`.
        NpcPortrait(npc = npc, name = strings[npc.nameKey], size = 36.dp)
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = strings[npc.nameKey],
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            // Omitted when empty, as the opponent list does: an absent line already says
            // "no special rules", and every rung here has some.
            if (npc.ruleKeys.isNotEmpty()) {
                Text(
                    text = npc.ruleKeys.joinToString(DOT_SEPARATOR) { strings[it] },
                    color = LocalTtoColors.current.transient,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Text(
            text = "${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * A ladder in progress: one rung at a time, on the ordinary match screen.
 *
 * `CCGroupMatchScreen` and `GSGroupMatchScreen` are `PVEMatchScreen` with a step counter, an
 * opponent table and some dialogue. All three are [MatchScript] here, so what is left is the state
 * machine: which rung, and where the result sends it.
 *
 * ### Where a result sends you
 *
 * [Campaign.nextStep] holds the rule and its KDoc the citations: a win advances, **a loss returns
 * to the first rung**, and a draw replays the same one. Past the last rung there is no opponent, so
 * [ScriptExit] is null and the end panel shows only Back — which is how `CCGroupRematchPanel` ends
 * a ladder, by not building the button.
 *
 * A rung is a whole match: `nextLesson` increments `STARTED_MATCHES` and `PVE_MATCHES` before
 * dispatching, and [MatchScreen] does the same on the way in, so the counters agree without this
 * screen touching them.
 *
 * @param onFinished the ladder is over, or the player has left it.
 */
@Composable
@Suppress("LongParameterList")
internal fun CampaignMatchScreen(
    campaign: Campaign,
    catalog: CardCatalog,
    profile: GameSave,
    clock: Clock,
    onPersist: suspend (GameSave) -> Unit,
    onFinished: () -> Unit,
) {
    var step by remember(campaign.key) { mutableStateOf(Campaign.FIRST_STEP) }
    var result by remember(campaign.key) { mutableStateOf<MatchResult?>(null) }
    val entry = campaign.stepAt(step) ?: return

    val script = remember(entry) {
        MatchScript(
            speakerKey = entry.npc.nameKey,
            lesson = Lesson.opening(entry.messages.start),
            outcomeLines = MatchResult.entries
                .mapNotNull { outcome -> entry.messages.forResult(outcome)?.let { outcome to it } }
                .toMap(),
        )
    }

    // Built from the result, so it can only exist once one is known — which is also the only time
    // the panel holding it is on screen.
    val exit = result
        ?.let { campaign.nextStep(step, it) }
        ?.takeIf { campaign.stepAt(it) != null }
        ?.let { next ->
            ScriptExit(StringKeys.NEXT_MATCH) {
                result = null
                step = next
            }
        }

    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = LocalStrings.current.format(
                StringKeys.CAMPAIGN_STEP,
                "${step + 1}",
                "${campaign.steps.size}",
            ),
            color = LocalTtoColors.current.transient,
            style = MaterialTheme.typography.labelSmall,
            modifier = Modifier
                .testTag(CAMPAIGN_STEP_TEST_TAG)
                .fillMaxWidth()
                .padding(horizontal = 12.dp),
        )
        // Keyed on the rung rather than trusting the opponent to differ: `MatchScreen` re-deals on
        // `npc.iconId`, and two rungs of one ladder sharing an icon would silently keep the board.
        key(step) {
            MatchScreen(
                catalog = catalog,
                profile = profile,
                npc = entry.npc,
                clock = clock,
                onPersist = onPersist,
                onExit = onFinished,
                script = script,
                scriptExit = exit,
                onResult = { result = it },
            )
        }
    }
}

/**
 * What to call a ladder — its AS3 key when a bundle defines it, and this port's when none does.
 *
 * Both original keys are broken, in different ways, and this is the smallest honest repair:
 *
 * - **`STR_CCGROUP` is in none of the four bundles.** The Card Club's own panel title has always
 *   rendered as the literal text `STR_CCGROUP`, in every language the game shipped in.
 * - **`STR_GSGROUP` is only in `fr_FR`** ("Carré des cartes du Gold Saucer"). English, German and
 *   Japanese showed the key.
 *
 * Preferring the AS3 key where it exists keeps the original's own wording — a French player still
 * reads the sentence Square Enix wrote — and the `APP_` fallback means nobody reads a key.
 */
internal fun campaignTitle(strings: Strings, campaign: Campaign): String =
    if (strings.has(campaign.nameKey)) {
        strings[campaign.nameKey]
    } else {
        strings["APP_CAMPAIGN_${campaign.key.uppercase()}"]
    }
