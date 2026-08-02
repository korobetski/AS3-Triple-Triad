package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.data.NpcCatalog
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.model.GameSave
import com.tripletriad.model.MatchResult
import com.tripletriad.model.Npc

const val OPPONENT_LIST_TEST_TAG: String = "opponent-list"
const val OPPONENT_EMPTY_TEST_TAG: String = "opponent-empty"

/** `opponent-row-<iconId>` — unique across both tables, which the NPC `id` is not. */
fun opponentRowTestTag(iconId: String): String = "opponent-row-$iconId"

/**
 * Who the profile can challenge — the original's `PVEScreen`.
 *
 * Two filters, both from the data and neither invented here:
 *
 * - **the collection**, so an `ff14_` profile never meets an `ff8_` opponent. `NPCs.LIST` returns
 *   `NPCs[MODE.toUpperCase() + 'NPCS']`, so the tables are disjoint by construction.
 * - **the hour**, because 27 of the 60 ff14 opponents declare an availability window and half of
 *   those wrap midnight. This is the only thing in the app that reads a wall clock, which is why
 *   [com.tripletriad.time.Clock] exists.
 *
 * A row shows what the player needs in order to choose: the level band that sets the XP, the
 * difficulty, the fee, the MGP on a win, and **the rules the opponent imposes** — that last one
 * matters most, since Reverse or Fallen Ace changes how the whole match is played and the original
 * only revealed it once the board was already up.
 */
@Composable
internal fun OpponentScreen(
    profile: GameSave,
    catalog: NpcCatalog,
    hour: Int,
    onChallenge: (Npc) -> Unit,
    onBack: () -> Unit,
) {
    val strings = LocalStrings.current
    val opponents = remember(catalog, profile.mode, hour) {
        catalog.available(profile.mode, hour)
    }

    ScreenScaffold(
        title = "${strings[StringKeys.OPPONENTS]} ${DOT_SEPARATOR}${collectionLabel(profile.mode)}",
        onBack = onBack,
    ) {
        if (opponents.isEmpty()) {
            Text(
                text = strings[StringKeys.NO_OPPONENT],
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                modifier = Modifier.testTag(OPPONENT_EMPTY_TEST_TAG).padding(vertical = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.testTag(OPPONENT_LIST_TEST_TAG).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(opponents, key = { it.iconId }) { npc ->
                    OpponentRow(npc = npc, onClick = { onChallenge(npc) })
                }
            }
        }
    }
}

@Composable
private fun OpponentRow(npc: Npc, onClick: () -> Unit) {
    val strings = LocalStrings.current

    Column(
        modifier = Modifier
            .testTag(opponentRowTestTag(npc.iconId))
            .fillMaxWidth()
            .clip(RowShape)
            .background(RowBackground)
            .border(1.dp, RowBorder, RowShape)
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = strings[npc.nameKey],
                color = Color.White,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = strings[npc.level.labelKey],
                color = BlueCard,
                fontSize = 11.sp,
                maxLines = 1,
                softWrap = false,
            )
        }

        Text(
            text = rewardLine(strings, npc),
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        // The rules line is omitted rather than shown empty: "no special rules" is what an absent
        // line already says, and a row that is always three lines tall wastes a third of a phone
        // list on the majority of opponents that impose nothing.
        val rules = npc.ruleKeys
        if (rules.isNotEmpty()) {
            Text(
                text = rules.joinToString(DOT_SEPARATOR) { strings[it] },
                color = RuleText,
                fontSize = 11.sp,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * `Difficulty 5 · Match Fee 20 · 47 MGP · 35 XP`.
 *
 * The MGP and XP shown are the **base** payout for a win, before the random top-up
 * ([com.tripletriad.data.MatchRewards]) and before any boon. Showing a range would be more honest
 * still, but `47-67 MGP` invites the reading that the fee is subtracted somewhere in there, and it
 * is not — see [Npc.mgpFor].
 */
private fun rewardLine(strings: Strings, npc: Npc): String = buildList {
    add("${strings[StringKeys.DIFFICULTY]} ${npc.difficulty}")
    if (npc.matchFee > 0) add("${strings[StringKeys.MATCH_FEE]} ${npc.matchFee}")
    add("${npc.mgpFor(MatchResult.WIN)} ${strings[StringKeys.MGP]}")
    val xp = npc.xpFor(MatchResult.WIN)
    if (xp > 0) add("$xp ${strings[StringKeys.XP]}")
}.joinToString(DOT_SEPARATOR)

private val RuleText = Color(0xFFF2C14E)
