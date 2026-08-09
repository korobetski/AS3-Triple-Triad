package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
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
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.Achievement
import com.tripletriad.model.AchievementCatalog
import com.tripletriad.model.GameSave
import com.tripletriad.model.XpTable
import com.tripletriad.ui.theme.LocalTtoColors
import kotlin.math.roundToInt

const val STATS_TABLE_TEST_TAG: String = "stats-table"
const val STATS_LEVEL_TEST_TAG: String = "stats-level"
const val STATS_ACHIEVEMENTS_TEST_TAG: String = "stats-achievements"
const val STATS_NO_ACHIEVEMENT_TEST_TAG: String = "stats-no-achievement"

/**
 * `stats-<label>` on a counter's **value**, by the key its row is labelled with.
 *
 * On the value and not on the row, so `assertTextEquals` reads the number: a `Row` carrying only a
 * `testTag` is not a merging semantics node and would report no text at all. The label is the
 * translation, and that is `StringsBundleTest`'s to check.
 */
fun statsRowTestTag(labelKey: String): String = "stats-$labelKey"

/** `stats-ac-<id>`, by the achievement's own id — `ac-tt1`, `ac-fob`. */
fun achievementRowTestTag(id: String): String = "stats-$id"

/**
 * The character's record and its achievements — the original's `profileScreen`.
 *
 * Two panels side by side on a 1024-wide stage there; one scrolling column here, statistics then
 * achievements, because the second panel is the long one and a phone has no second column to put it
 * in.
 *
 * ### The pie chart is a number
 *
 * `RoundChart` drew wins, defeats and draws as three arcs with the **total** in the middle and no
 * percentage anywhere (`:181-194`). A three-segment pie of numbers the list beside it already
 * prints is decoration; what it was standing in for is the win rate, which the original never wrote
 * down. So [Stats.winRate] is a row like the others. The arcs are Phase 6's business if they are
 * anyone's.
 *
 * Its `_total` also divides by zero on a fresh profile — `0 * 360 / 0` is `NaN` in AS3 and the
 * three ratios come out `NaN` — which draws nothing rather than crashing. [Stats.winRate] returns
 * 0f.
 *
 * ### Every achievement is listed, not only the earned ones
 *
 * `:210-220` walks `PROFILE_DATAS.ACHIEVEMENTS`, so an unearned achievement is invisible and the
 * screen cannot say what there is to aim at — the same defect the collection browser had before
 * `cardListScreen` walked the whole card table. [Requirement.progress] exists precisely so this
 * screen can ask how close the profile is, which a pre-computed Boolean could not answer; see
 * [com.tripletriad.model.Requirement].
 *
 * Earned first and in the order they were earned, newest first, which is the original's
 * `sortOn(['unlockDate', 'label'], DESCENDING)`. The **date itself is not shown**: `commonMain` has
 * no calendar — see `Clock`, and the reason `kotlinx-datetime` was dropped — so the timestamp
 * orders the list and nothing more.
 */
@Composable
internal fun StatsScreen(profile: GameSave, onBack: () -> Unit) {
    val strings = LocalStrings.current
    val achievements = remember(profile) { rankedAchievements(profile) }

    CharacterScaffold(profile = profile, title = strings[StringKeys.PROFILE], onBack = onBack) {
        LevelBar(profile)

        Column(
            modifier = Modifier
                .testTag(STATS_TABLE_TEST_TAG)
                .fillMaxWidth()
                .padding(top = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            StatRow(StringKeys.WINS, "${profile.stats.wins}")
            StatRow(StringKeys.DEFEATS, "${profile.stats.defeats}")
            StatRow(StringKeys.DRAWS, "${profile.stats.draws}")
            // Derived, not stored: `STARTED_MATCHES - ENDED_MATCHES`. See [GameSave.forfeits].
            StatRow(StringKeys.FORFEITS, "${profile.forfeits}")
            StatRow(StringKeys.MATCHES, "${profile.stats.played}")
            StatRow(StringKeys.WIN_RATE, "${(profile.stats.winRate * PERCENT).roundToInt()}%")
            StatRow(StringKeys.MGP, "${profile.mgp}")
            StatRow(
                StringKeys.BOONS,
                "${strings[StringKeys.MGP]} ×${profile.boons.mgp}$DOT_SEPARATOR" +
                    "${strings[StringKeys.XP]} ×${profile.boons.xp}",
            )
        }

        Text(
            text = strings[StringKeys.ACHIEVEMENTS_LIST],
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(top = 14.dp, bottom = 6.dp),
        )

        if (achievements.isEmpty()) {
            // Unreachable while [AchievementCatalog] has 22 members, and asserted anyway: an empty
            // catalogue should say so rather than render as a screen that lost its second half.
            EmptyNote(strings[StringKeys.NO_ACHIEVEMENT], STATS_NO_ACHIEVEMENT_TEST_TAG)
        } else {
            LazyColumn(
                modifier = Modifier
                    .testTag(STATS_ACHIEVEMENTS_TEST_TAG)
                    .fillMaxWidth()
                    .weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(achievements, key = { it.id }) { achievement ->
                    AchievementRow(
                        achievement = achievement,
                        profile = profile,
                        isEarned = profile.hasAchievement(achievement.id),
                    )
                }
            }
        }
    }
}

/**
 * The avatar, the level and how far into it the profile is — the original's `profile.jpg` header.
 *
 * `ProgressBar` between `Level.steps[level - 1]` and `steps[level]` (`:127-134`), now with the
 * portrait beside it that `GameSave.AVATAR_ID` has been naming since Phase 2 with nothing to draw.
 * The 27 FFXIV portraits are imported; **choosing** one still is not, so this shows the profile's
 * own and does not offer to change it. A picker is a screen, not a corner of this one.
 */
@Composable
internal fun LevelBar(profile: GameSave) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AvatarBadge(profile = profile)
        Box(modifier = Modifier.weight(1f)) { LevelMeter(profile) }
    }
}

@Composable
private fun LevelMeter(profile: GameSave) {
    val strings = LocalStrings.current
    val floor = XpTable.thresholdFor(profile.level)
    val ceiling = XpTable.thresholdFor(profile.level + 1)
    // At the top of the table there is no next threshold, so the bar is full rather than dividing
    // by a zero span — the AS3 read `steps[22]` as `undefined` here and drew nothing.
    val span = ceiling - floor
    val fraction = if (span <= 0L) 1f else ((profile.xp - floor).toFloat() / span).coerceIn(0f, 1f)

    Column(
        modifier = Modifier.testTag(STATS_LEVEL_TEST_TAG).fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = "${strings[StringKeys.LEVEL]} ${profile.level}$DOT_SEPARATOR" +
                "${profile.xp} ${strings[StringKeys.XP]}",
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
        )
        Meter(fraction = fraction, colour = MaterialTheme.colorScheme.tertiary)
    }
}

/** One counter: its name on the left, its value on the right. */
@Composable
private fun StatRow(labelKey: String, value: String) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .rowSurface()
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = strings[labelKey],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            softWrap = false,
            modifier = Modifier.testTag(statsRowTestTag(labelKey)),
        )
    }
}

@Composable
private fun AchievementRow(achievement: Achievement, profile: GameSave, isEarned: Boolean) {
    val strings = LocalStrings.current
    val progress = achievement.progressFor(profile)

    Column(
        modifier = Modifier
            .testTag(achievementRowTestTag(achievement.id))
            .fillMaxWidth()
            .rowSurface(selected = isEarned)
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            AchievementIcon(
                iconId = achievement.iconId,
                description = strings[achievement.labelKey],
                size = 28.dp,
            )
            Text(
                text = strings[achievement.labelKey],
                color = MaterialTheme.colorScheme.onSurface
                    .copy(alpha = if (isEarned) 1f else 0.65f),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Text(
                // The counter rather than a tick, because "300 of 3000" is the thing the original
                // could not show at all and a tick is what the highlighted row already says.
                text = "${progress.current} / ${progress.target}",
                color = if (isEarned) {
                    LocalTtoColors.current.transient
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT)
                },
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = strings["${achievement.labelKey}_DESC"],
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
            style = MaterialTheme.typography.labelSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        val fill = if (isEarned) {
            LocalTtoColors.current.transient
        } else {
            MaterialTheme.colorScheme.tertiary
        }
        Meter(fraction = progress.fraction, colour = fill)
    }
}

/** A filled bar — `feathers.controls.ProgressBar`, with no Material 3 counterpart worth the API. */
@Composable
private fun Meter(fraction: Float, colour: Color) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(MeterHeight)
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(fraction)
                .height(MeterHeight)
                .clip(MaterialTheme.shapes.small)
                .background(colour),
        )
    }
}

/**
 * Earned first, newest earned first within that; then the rest, closest first.
 *
 * The earned half is the original's `sortOn(['unlockDate'], DESCENDING)`. The unearned half is new,
 * and ordering it by how close the profile is puts the next thing to aim at at the top of it rather
 * than whichever tier the catalogue happens to declare first.
 */
private fun rankedAchievements(profile: GameSave): List<Achievement> {
    val (earned, pending) = AchievementCatalog.all.partition { profile.hasAchievement(it.id) }
    return earned.sortedByDescending { profile.achievements[it.id] ?: 0L } +
        pending.sortedByDescending { it.progressFor(profile).fraction }
}

private val MeterHeight = 4.dp
private const val PERCENT = 100
