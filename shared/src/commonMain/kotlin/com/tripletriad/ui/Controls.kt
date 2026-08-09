package com.tripletriad.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.tripletriad.audio.LocalAudio
import com.tripletriad.audio.Sound
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.model.GameSave
import com.tripletriad.ui.theme.LocalTtoColors

/** The back chevron of any [ScreenScaffold]. Only one is on screen at a time. */
const val SCREEN_BACK_TEST_TAG: String = "screen-back"

/** The [CharacterActions]. Their presence is what says a screen is behind the dashboard. */
const val CHARACTER_BAR_TEST_TAG: String = "character-bar"

/**
 * A full-width action button.
 *
 * The menu's stack and every screen's primary action are the same control, so it is one composable.
 * `softWrap = false` with ellipsis rather than a second line: `STR_BACKGROUND_VOLUME` in German is
 * `Hintergrundlautstärke`, and a stack whose rows change height by language is a stack that jumps
 * when the language does. A truncated label is a visible problem; a layout that shifts between
 * languages is a subtle one.
 *
 * @param filled false for the quiet half of a pair — the same button in the theme's surface colours
 *   rather than its primary. Two filled buttons side by side ask the player to choose between two
 *   equally loud things; Material's own dialog pairs one filled action with one that is not.
 */
@Composable
internal fun WideButton(
    label: String,
    tag: String,
    enabled: Boolean = true,
    filled: Boolean = true,
    onClick: () -> Unit,
) {
    // `TouchLabel.as:31` played this on any tap on a control, so it belongs to the control and not
    // to each caller — otherwise the next screen added is the one that forgets it.
    val audio = LocalAudio.current
    Button(
        onClick = {
            audio.play(Sound.UI_CLICK)
            onClick()
        },
        enabled = enabled,
        modifier = Modifier.fillMaxWidth().height(48.dp).testTag(tag),
        shape = MaterialTheme.shapes.extraSmall,
        // `primary` is the card blue and `onPrimary` the theme's light text, so the only thing
        // left to say is what a *disabled* button looks like — Material would grey it against a
        // surface this app does not use.
        colors = ButtonDefaults.buttonColors(
            containerColor = if (filled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.surfaceVariant
            },
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DISABLED),
        ),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * An app bar, an optional snackbar, and a column for the screen's own content.
 *
 * Extracted because the profile, creation and opponent screens would otherwise repeat the same
 * header, the same max width and the same padding, and the first one to be edited alone is the one
 * that starts looking different.
 *
 * ### Why a real [TopAppBar] and not the two-`Text` row this used to be
 *
 * The row worked and cost nothing, and that is exactly what was wrong with it: it had no touch
 * target worth the name — a 20 sp glyph with 4 dp of padding is 28 dp of tappable width against
 * Material's 48 — no elevation when content scrolled beneath it, and no slot for anything but the
 * title. Every one of those is something the bar has to grow the moment a screen wants an action in
 * the corner, and the purse is that action on eleven screens.
 *
 * The bar is held to the content's own width and centred rather than spanning the window, so its
 * title sits over the column it belongs to. A full-bleed bar is the phone-shaped answer; on a
 * desktop window three times as wide it would strand the title a hand's width from its own list.
 *
 * @param actions the corner of the bar — the purse, on every screen behind the dashboard.
 * @param snackbar where transient confirmations land, or null on a screen that has none. Hoisted
 *   rather than created here because the message comes from the screen: only it knows what was
 *   bought, saved or refused.
 * @param bottomBar a screen's one committing action — Buy, Confirm — pinned below the content.
 *   Worth a slot of its own rather than being the last row of [content] because a snackbar is
 *   placed *above* the bottom bar and *over* the content: with the button in the column, the
 *   confirmation for a purchase lands on top of the button that makes the next one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ScreenScaffold(
    title: String,
    onBack: () -> Unit,
    actions: @Composable RowScope.() -> Unit = {},
    snackbar: NoteHost? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    wide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    val strings = LocalStrings.current
    val navigation = LocalNavigation.current
    val isWide = LocalWideLayout.current
    // The navigation, if it is going down the left edge — null when it is along the bottom or when
    // there is none. One value rather than a boolean beside a nullable, so that "the rail is up"
    // and "there is something to put in it" cannot disagree: the rail *replaces* the bar, and two
    // sets of the same four entries is the one thing an adaptive layout must not do.
    val rail = navigation.takeIf { isWide }
    val columnWidth = if (wide && isWide) WideContentMaxWidth else ContentMaxWidth

    Row(modifier = Modifier.fillMaxSize()) {
        rail?.let { SideNavigation(it) }

        Scaffold(
            // The app draws its own backdrop under everything — see `App` — and a Scaffold that
            // painted `surface` over it would put a lighter rectangle behind every screen.
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
            topBar = {
                Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                    TopAppBar(
                        modifier = Modifier.widthIn(max = columnWidth),
                        title = {
                            Text(
                                text = title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        },
                        navigationIcon = {
                            IconButton(
                                onClick = onBack,
                                modifier = Modifier.testTag(SCREEN_BACK_TEST_TAG),
                            ) {
                                Icon(
                                    imageVector = TtoIcons.Back,
                                    contentDescription = strings[StringKeys.BACK],
                                )
                            }
                        },
                        actions = actions,
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = Color.Transparent,
                            titleContentColor = MaterialTheme.colorScheme.onBackground,
                            navigationIconContentColor =
                            MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                            actionIconContentColor = MaterialTheme.colorScheme.onBackground,
                        ),
                    )
                }
            },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    bottomBar?.let { bar ->
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 8.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                modifier = Modifier
                                    .widthIn(max = columnWidth)
                                    .fillMaxWidth(),
                            ) {
                                bar()
                            }
                        }
                    }
                    // Below the screen's own action, because it leaves the screen and the action
                    // does not: the thing nearest the thumb should act on what is above it. Absent
                    // outside a character's shell, and on a wide window where the rail has it.
                    if (rail == null) navigation?.let { BottomNavigation(it) }
                }
            },
            snackbarHost = {
                snackbar?.let { host ->
                    SnackbarHost(hostState = host.state) { data ->
                        // Tagged rather than found by text: a confirmation names the thing it is
                        // about — a card, a pack — so its wording is data, and a test asserting on
                        // it would be asserting on the catalogue.
                        Snackbar(snackbarData = data, modifier = Modifier.testTag(host.tag))
                    }
                }
            },
        ) { inset ->
            Column(
                modifier = Modifier.fillMaxSize().padding(inset),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = columnWidth)
                        .fillMaxWidth()
                        .weight(1f)
                        .padding(horizontal = 12.dp),
                    content = content,
                )
            }
        }
    }
}

/** `tab-<key>` — one per tab of a screen that has them, and no screen has two of a name. */
fun screenTabTestTag(key: String): String = "tab-$key"

/**
 * The two halves of a screen that holds two.
 *
 * Material's own [PrimaryTabRow], with the labels and the tags supplied by the caller. Two screens
 * use it — the collection and the store — and both hold **things that are the same kind of thing**:
 * cards you own and decks you build them into, what is for sale and what you bought. That is the
 * test for whether a tab row is the right control rather than a second destination; the record and
 * the rules are not tabs of each other and are not on one.
 *
 * @param tabs the label and the tag of each, in order.
 * @param selected which is showing, as an index into [tabs].
 */
@Composable
internal fun ScreenTabs(
    tabs: List<Pair<String, String>>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val audio = LocalAudio.current

    PrimaryTabRow(
        selectedTabIndex = selected,
        modifier = modifier.fillMaxWidth(),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        tabs.forEachIndexed { index, (label, tag) ->
            Tab(
                selected = index == selected,
                onClick = {
                    audio.play(Sound.UI_CLICK)
                    onSelect(index)
                },
                modifier = Modifier.testTag(tag),
                text = {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelLarge,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                selectedContentColor = MaterialTheme.colorScheme.onBackground,
                unselectedContentColor =
                MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
            )
        }
    }
}

/**
 * A screen's transient confirmations, and the tag they are found by.
 *
 * A [SnackbarHostState] with a name attached, because the two always travel together: the host is
 * useless to a test that cannot find what it showed, and every screen that has one has exactly one.
 *
 * [show] replaces rather than queues. Material's default is a queue — a second `showSnackbar`
 * suspends until the first has run its four seconds — which is right for messages that must each be
 * read and wrong for these: buying three packs in a row should say what the *third* one was, not
 * make the player wait twelve seconds to be told.
 */
@Stable
internal class NoteHost(val tag: String) {
    val state: SnackbarHostState = SnackbarHostState()

    suspend fun show(message: String) {
        state.currentSnackbarData?.dismiss()
        state.showSnackbar(message = message, duration = SnackbarDuration.Short)
    }
}

@Composable
internal fun rememberNoteHost(tag: String): NoteHost = remember(tag) { NoteHost(tag) }

/**
 * The character's level, purse and active boosts, for the corner of the app bar.
 *
 * `display/UserBar.as`, which every dashboard screen put in its top-right corner — which is where
 * this is again, after a spell as a full-width band under the title. Two things of the original's
 * are still not here: the **jump menu** it opened on tap, which listed every dashboard screen
 * except the current one and existed because the original had no back button, and the character's
 * **name**, which is the app bar's own title on the one screen where it is the subject.
 *
 * The boon markers are shown as `MGP ×n` / `XP ×n` rather than as the original's two icons, because
 * a boon is a **count of boosted matches** and the icon said only that there was at least one — see
 * [com.tripletriad.model.Boons.spending].
 */
@Composable
internal fun CharacterActions(save: GameSave) {
    val strings = LocalStrings.current
    val boons = buildList {
        if (save.boons.mgp > 0) add("${strings[StringKeys.MGP]} ×${save.boons.mgp}")
        if (save.boons.xp > 0) add("${strings[StringKeys.XP]} ×${save.boons.xp}")
    }

    Row(
        modifier = Modifier.testTag(CHARACTER_BAR_TEST_TAG).padding(end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (boons.isNotEmpty()) {
            Text(
                text = boons.joinToString(" "),
                color = LocalTtoColors.current.transient,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = "${strings[StringKeys.LEVEL]} ${save.level}",
            color = MaterialTheme.colorScheme.onSurface.copy(alpha = SUBDUED),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            softWrap = false,
        )
        // The purse as the game's own coin rather than the letters `MGP`: it is the one number on
        // this bar the player is tracking, and `icons/PGS.png` is what the original marked it with.
        ItemIcon(iconId = "PGS", description = strings[StringKeys.MGP], size = PURSE_ICON)
        Text(
            text = "${save.mgp}",
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** [ScreenScaffold] with the purse in its corner — every screen behind the dashboard. */
@Composable
internal fun CharacterScaffold(
    profile: GameSave,
    title: String,
    onBack: () -> Unit,
    snackbar: NoteHost? = null,
    bottomBar: (@Composable () -> Unit)? = null,
    wide: Boolean = false,
    content: @Composable ColumnScope.() -> Unit,
) {
    ScreenScaffold(
        title = title,
        onBack = onBack,
        actions = { CharacterActions(profile) },
        snackbar = snackbar,
        bottomBar = bottomBar,
        wide = wide,
        content = content,
    )
}

/**
 * A centred "there is nothing here" line.
 *
 * Its own composable because the tag is the assertion: `assertDoesNotExist` on a list is not the
 * same claim as "the screen says it is empty", and the four screens that can be empty should all
 * make the second one.
 */
@Composable
internal fun EmptyNote(text: String, tag: String) {
    Text(
        text = text,
        color = MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.testTag(tag).padding(vertical = 24.dp),
    )
}

/**
 * The shared list-row surface: rounded, filled, outlined, and tappable.
 *
 * Six screens draw this same box. A modifier rather than a wrapper composable so a row keeps
 * control of its own layout — some are a `Row`, some a `Column`, and one is a grid cell.
 *
 * `@Composable` because it reads the theme, which is what a `Modifier` extension may do as long as
 * it is called from a composition — every call site here is inside one.
 *
 * @param armed draws the destructive-confirmation outline instead of the ordinary one.
 * @param selected draws the card-blue outline and tints the fill, for a row that is the current
 *   choice rather than merely tappable.
 */
@Composable
internal fun Modifier.rowSurface(
    armed: Boolean = false,
    selected: Boolean = false,
): Modifier {
    val game = LocalTtoColors.current
    val shape = MaterialTheme.shapes.small
    return clip(shape)
        .background(if (selected) game.selectedFill else MaterialTheme.colorScheme.surfaceVariant)
        .border(
            width = 1.dp,
            color = when {
                armed -> MaterialTheme.colorScheme.error
                selected -> game.selectedOutline
                else -> MaterialTheme.colorScheme.outline
            },
            shape = shape,
        )
}

/** Keeps every list screen the same width on a desktop window that is far wider than a phone. */
internal val ContentMaxWidth = 520.dp

/**
 * What a screen gets instead when it lays out two panes and the window is [LocalWideLayout].
 *
 * Only the screens that opt in take it. A list does not become more readable at 900 dp — it becomes
 * a row of text with a hand's width of nothing in the middle — so widening every screen because the
 * window allows it would be spending the space rather than using it.
 */
internal val WideContentMaxWidth = 920.dp

/** The `·`-joined metadata line used by the profile and opponent rows. */
internal const val DOT_SEPARATOR = "  ·  "

/*
 * The alphas this app dims text by. Four steps, named once: a screen with six shades of white is a
 * screen where each was picked separately.
 */

/** A secondary line under a row's name. */
internal const val SUBDUED = 0.75f

/** An explanatory line, and an empty-state note. */
internal const val MUTED = 0.7f

/** Metadata that should recede: a count, a rarity, a description. */
internal const val FAINT = 0.6f

/** A disabled control's own label. */
internal const val DISABLED = 0.4f

/** The purse coin in the app bar. Smaller than a bag row's icon, which is a 32 dp plate. */
private val PURSE_ICON = 18.dp
