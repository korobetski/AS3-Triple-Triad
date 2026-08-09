package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.Strings
import com.tripletriad.net.ServerEntry
import com.tripletriad.net.ServerStatus
import com.tripletriad.net.downloadForThisPlatform
import com.tripletriad.net.latency
import com.tripletriad.platform.rememberUrlOpener
import com.tripletriad.ui.theme.LocalTtoColors
import kotlinx.coroutines.launch

const val SERVERS_SCREEN_TEST_TAG: String = "servers-screen"
const val SERVERS_REFRESH_TEST_TAG: String = "servers-refresh"
const val UPDATE_NOTICE_TEST_TAG: String = "update-notice"
const val UPDATE_DOWNLOAD_TEST_TAG: String = "update-download"

/** One row per configured server. Suffixed with the entry id, which is stable and unique. */
fun serverRowTestTag(entry: ServerEntry): String = "server-${entry.id}"

/**
 * The servers this build offers, what each one is doing, and which one is in play.
 *
 * ### Why the player gets to choose at all
 *
 * Because there is more than one right answer. A player near one region should not be paying
 * two hundred milliseconds a move to reach another, a self-hosted deployment is a legitimate place
 * to keep a character, and a host that is down should not make the game unplayable when another is
 * up. The alternative — one hard-coded address — turns every one of those into "the game is
 * broken".
 *
 * ### What switching costs, and why the screen says so
 *
 * The account. A token is only valid on the host that issued it and the character it names does not
 * exist elsewhere, so moving signs the player out — see [AccountSession.useServer]. That is not a
 * bug to hide behind a spinner; it is the thing the player needs to know *before* they tap,
 * which is why the note is above the list rather than in a dialog after it.
 *
 * What switching does **not** cost is progress. Sessions and unsubmitted matches are stored per
 * server, so the old host still has both when the player comes back.
 *
 * @param onSelect what to do with a chosen entry. Suspending, and given the entry rather than being
 *   a plain `() -> Unit` per row, because the switch is a sign-out and a restore and the screen
 *   should stay on top of the list while it happens.
 */
@Composable
internal fun ServersScreen(
    connectivity: Connectivity,
    onSelect: suspend (ServerEntry) -> Unit,
    onBack: () -> Unit,
) {
    val scope = rememberCoroutineScope()

    // Every server, on arrival. This is the one screen where the whole set is on display at once,
    // and a list of "unknown" that the player has to press refresh to populate would be a list that
    // makes them do the app's job.
    LaunchedEffect(connectivity) { connectivity.refreshAll() }

    val strings = LocalStrings.current

    ScreenScaffold(
        title = strings[StringKeys.SERVERS],
        onBack = onBack,
        // In the bar rather than under the list, where it used to be pushed off screen by a long
        // update notice — the same slot the store's Buy button took, and for the same reason.
        bottomBar = {
            WideButton(
                label = strings[
                    if (connectivity.isProbing) {
                        StringKeys.SERVERS_CHECKING
                    } else {
                        StringKeys.SERVERS_CHECK
                    },
                ],
                tag = SERVERS_REFRESH_TEST_TAG,
                enabled = !connectivity.isProbing,
                filled = false,
                onClick = { scope.launch { connectivity.refreshAll() } },
            )
        },
    ) {
        Column(
            modifier = Modifier.testTag(SERVERS_SCREEN_TEST_TAG).fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = strings[StringKeys.SERVERS_BLURB],
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
                style = MaterialTheme.typography.labelMedium,
            )

            connectivity.update?.let { UpdateNotice(it) }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(connectivity.servers, key = { it.id }) { entry ->
                    ServerRow(
                        entry = entry,
                        status = connectivity.statusOf(entry),
                        isSelected = entry.id == connectivity.selected.id,
                        onClick = { scope.launch { onSelect(entry) } },
                    )
                }
            }
        }
    }
}

/**
 * One server: a status dot, its label and address, and what the last probe learned.
 *
 * The name shown is the **configured** one and not the one the server calls itself. A
 * deployment can call itself anything, including what another one calls itself, and a list where
 * two rows are labelled the same is a list the player cannot choose from. The address is under it
 * for the same reason: it is the thing that is actually unique.
 */
@Composable
private fun ServerRow(
    entry: ServerEntry,
    status: ServerStatus,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val strings = LocalStrings.current

    Row(
        modifier = Modifier
            .testTag(serverRowTestTag(entry))
            .fillMaxWidth()
            .rowSurface(selected = isSelected)
            .clickable(enabled = !isSelected, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        StatusDot(status)
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = entry.label,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = entry.baseUrl,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = status.describe(strings),
            color = status.tint(),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/**
 * A one-line status the main menu can carry, tappable to open the list.
 *
 * Deliberately small and deliberately always present. A banner that appears only when something is
 * wrong is a banner that moves the layout at the worst moment, and a player who has never seen it
 * green has no idea what it means when it turns red.
 */
@Composable
internal fun ServerIndicator(connectivity: Connectivity, onClick: () -> Unit) {
    val status = connectivity.status
    val strings = LocalStrings.current
    Row(
        modifier = Modifier
            .testTag(MENU_SERVER_TEST_TAG)
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        StatusDot(status)
        Text(
            text = "${connectivity.selected.label}$DOT_SEPARATOR${status.describe(strings)}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * "There is a newer build, and here is where to get it."
 *
 * ### Why a link and not an update
 *
 * Because the app is not allowed to be the updater on two of its three platforms and should not be
 * on the third — the reasoning is in [rememberUrlOpener]. What is left is doing the one useful
 * thing well: saying which version, saying whether the server will still serve this one, and
 * putting the right artifact for *this* platform one tap away.
 *
 * The button is absent, rather than disabled, when the deployment publishes no download for this
 * platform. A greyed-out button implies the player is doing something wrong; the truth is that
 * there is nothing on the other end of it.
 */
@Composable
internal fun UpdateNotice(advice: UpdateAdvice) {
    val strings = LocalStrings.current
    val open = rememberUrlOpener()
    val download = advice.info.downloadForThisPlatform()
    val game = LocalTtoColors.current

    Column(
        modifier = Modifier
            .testTag(UPDATE_NOTICE_TEST_TAG)
            .fillMaxWidth()
            .rowSurface()
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = strings[
                if (advice.isRequired) StringKeys.UPDATE_REQUIRED else StringKeys.UPDATE_AVAILABLE,
            ],
            color = if (advice.isRequired) MaterialTheme.colorScheme.error else game.transient,
            style = MaterialTheme.typography.titleSmall,
        )
        Text(
            text = strings.format(
                if (advice.isRequired) {
                    StringKeys.UPDATE_REQUIRED_BODY
                } else {
                    StringKeys.UPDATE_AVAILABLE_BODY
                },
                advice.target.toString(),
            ),
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = MUTED),
            style = MaterialTheme.typography.labelMedium,
        )
        advice.info.release?.notes?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = FAINT),
                style = MaterialTheme.typography.labelSmall,
            )
        }
        if (download != null) {
            WideButton(
                label = strings.format(StringKeys.UPDATE_GET, advice.target.toString()),
                tag = UPDATE_DOWNLOAD_TEST_TAG,
                onClick = { open(download) },
            )
        }
    }
}

/** The coloured dot. The whole status, at a glance, in eight dp. */
@Composable
private fun StatusDot(status: ServerStatus) {
    Box(
        modifier = Modifier
            .size(DotSize)
            .background(status.tint(), CircleShape),
    )
}

/**
 * What to say about a status in one short phrase.
 *
 * Kept here rather than on [ServerStatus] because it is wording, and the status lives in the
 * network layer — the same division the account screen's wording draws. The latency
 * is folded in rather than given its own column: it is only meaningful for the two states that have
 * one, and an empty column for the other five is a column that mostly says nothing.
 */
private fun ServerStatus.describe(strings: Strings): String = when (this) {
    ServerStatus.Unknown -> strings[StringKeys.SERVER_UNKNOWN]
    ServerStatus.Checking -> strings[StringKeys.SERVER_CHECKING]
    // The latency is appended rather than interpolated into the phrase: it is absent for a probe
    // that did not time one, and a `{0}` with nothing in it would leave a gap mid-sentence.
    is ServerStatus.Online ->
        strings[StringKeys.SERVER_ONLINE] + latency?.let { "$DOT_SEPARATOR${it}ms" }.orEmpty()
    is ServerStatus.Degraded -> strings[StringKeys.SERVER_DEGRADED]
    is ServerStatus.Outdated -> strings[StringKeys.SERVER_OUTDATED]
    is ServerStatus.Unreachable -> strings[StringKeys.SERVER_UNREACHABLE]
    is ServerStatus.Unusable -> strings[StringKeys.SERVER_UNUSABLE]
}

/**
 * The colour a status is drawn in.
 *
 * Four colours for seven states, on purpose: what the player acts on is *green, wait, act, broken*,
 * and giving each state its own hue would be seven things to learn instead of four.
 */
@Composable
private fun ServerStatus.tint(): Color = when (this) {
    is ServerStatus.Online -> Healthy
    is ServerStatus.Degraded, ServerStatus.Checking -> LocalTtoColors.current.transient
    is ServerStatus.Outdated -> LocalTtoColors.current.transient
    is ServerStatus.Unreachable, is ServerStatus.Unusable -> MaterialTheme.colorScheme.error
    ServerStatus.Unknown -> MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED)
}

/** The one colour the theme has no entry for: the original palette is warm and has no green. */
private val Healthy = Color(0xFF5FA85F)

private val DotSize = 8.dp
