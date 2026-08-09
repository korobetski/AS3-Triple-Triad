package com.tripletriad.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys

/** `nav-<tab>` — the four entries, under whichever of the two containers is drawn. */
fun navTestTag(tab: String): String = "nav-$tab"

/**
 * The bar and the rail carry the **same four tags**, so a test that only drives navigation does not
 * have to know which is on screen. These two say which one it is, for the tests that do.
 */
const val NAV_BAR_TEST_TAG: String = "nav-bar"
const val NAV_RAIL_TEST_TAG: String = "nav-rail"

/**
 * The four places a loaded character can be.
 *
 * ### Why four, and why these four
 *
 * The dashboard listed eight destinations. Material's navigation bar takes three to five, and the
 * count is not a style rule: past five the targets are narrower than a thumb and the labels start
 * truncating in German. Two of the eight were pairs and became one screen each — see
 * [CollectionScreen] and [StoreScreen] — and two more, the record and the rules, are things a
 * player opens occasionally from [HOME] rather than switches to. That leaves exactly four that are
 * *modes of playing* rather than *pages*, which is the test a bar entry has to pass.
 *
 * ### Why this needs no back stack
 *
 * A tabbed shell usually does: with several tab histories, "up" stops being a property of a screen.
 * Not here — every one of these roots already has [Screen.DASHBOARD] as its [Screen.up], because
 * the dashboard was the parent of all eight. So back from any tab lands on Home, which is also what
 * Material prescribes for a bar (`popUpTo(startDestination)`), and `Screen.up` was already saying
 * it. The one screen with a history of its own is the deck editor, and that is one boolean inside
 * [CollectionScreen].
 */
internal enum class Tab(val root: Screen, val labelKey: String, val icon: ImageVector) {
    HOME(Screen.DASHBOARD, StringKeys.HOME, TtoIcons.Home),
    PLAY(Screen.OPPONENTS, StringKeys.PLAY, TtoIcons.Play),
    CARDS(Screen.CARDS, StringKeys.CARDS, TtoIcons.Collection),
    STORE(Screen.SHOP, StringKeys.SHOP, TtoIcons.Shop),
}

/**
 * Which tab a screen belongs to, or null for one that is outside the bar entirely.
 *
 * The record and the rules answer [Tab.HOME] rather than null: they are opened from the dashboard
 * and they are still *in* the character's shell, so the bar stays and shows where they hang from.
 * A bar that vanished on the two screens reachable from Home would be a bar that flickers.
 *
 * The seven screens ahead of a character — splash, menu, the two chooser flows, the servers, the
 * options — and the three match screens answer null. A match is immersive by design; the others
 * have no character and so no bar to draw.
 */
internal val Screen.tab: Tab?
    get() = when (this) {
        Screen.DASHBOARD, Screen.STATS, Screen.HELP -> Tab.HOME
        Screen.OPPONENTS -> Tab.PLAY
        Screen.CARDS, Screen.DECKS -> Tab.CARDS
        Screen.SHOP, Screen.INVENTORY -> Tab.STORE
        Screen.SPLASH, Screen.MENU, Screen.PROFILES, Screen.PROFILE_NEW,
        Screen.ACCOUNT, Screen.SERVERS, Screen.OPTIONS,
        Screen.MATCH, Screen.TUTORIAL, Screen.CAMPAIGN, Screen.CAMPAIGN_MATCH,
        -> null
    }

/**
 * The bar's state, for the tree under a loaded character.
 *
 * A composition local rather than two more parameters on eleven screens. That is the trade this
 * makes and it is worth naming: a local is invisible at the call site, which is the usual argument
 * against one — but the alternative is threading `current` and `onSelect` through
 * [CharacterScaffold] and every screen that calls it, including the four that never look at either.
 * [LocalUiArt] is provided for the same reason and stated the same way.
 *
 * Null outside the character's shell, which is what makes [ScreenScaffold] draw no bar on the menu.
 */
@Immutable
internal class Navigation(val current: Tab?, val onSelect: (Tab) -> Unit)

internal val LocalNavigation = staticCompositionLocalOf<Navigation?> { null }

/**
 * Whether the window is wide enough for a rail and two panes.
 *
 * ### The threshold, and why it is a number here rather than a dependency
 *
 * 600 dp is Material's own `WindowWidthSizeClass.Compact`/`Medium` boundary, and the
 * `material3-adaptive-*` artifacts exist to compute it: `NavigationSuiteScaffold` would pick the
 * bar or the rail, `ListDetailPaneScaffold` would arrange the two panes. Both are separate
 * artifacts whose Compose Multiplatform publication would have to be checked target by target,
 * and what they would replace here is one comparison and one `if`. That is not a trade worth
 * making for two screens; it is worth revisiting the day a third pane or a real back stack appears.
 *
 * Measured once, in [App], off the whole window rather than per screen — every screen's answer
 * would otherwise depend on the padding above it, and the rail and the panes have to agree.
 */
internal val LocalWideLayout = compositionLocalOf { false }

/** Material's compact/medium boundary. A phone in landscape clears it; a phone upright does not. */
internal val WideLayoutThreshold = 600.dp

/**
 * The same four entries as [BottomNavigation], down the left edge.
 *
 * Which is the layout the original had: `card_list.jpg` is a 1024-wide stage with its controls on
 * one side and its content beside them, and this port had been drawing a 520 dp column in the
 * middle of a desktop window with nothing either side of it.
 */
@Composable
internal fun SideNavigation(state: Navigation) {
    val strings = LocalStrings.current

    NavigationRail(
        modifier = Modifier.testTag(NAV_RAIL_TEST_TAG),
        containerColor = Color.Transparent,
        contentColor = MaterialTheme.colorScheme.onBackground,
    ) {
        for (tab in Tab.entries) {
            NavigationRailItem(
                selected = tab == state.current,
                onClick = { state.onSelect(tab) },
                modifier = Modifier.testTag(navTestTag(tab.name.lowercase())),
                icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                label = { Text(text = strings[tab.labelKey], maxLines = 1) },
                colors = NavigationRailItemDefaults.colors(
                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                    selectedTextColor = MaterialTheme.colorScheme.onBackground,
                    indicatorColor = MaterialTheme.colorScheme.primary,
                    unselectedIconColor =
                    MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                    unselectedTextColor =
                    MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                ),
            )
        }
    }
}

/**
 * Material's [NavigationBar], with the four entries [Tab] declares.
 *
 * Held to [ContentMaxWidth] and centred for the reason the app bar is: on a desktop window the
 * four targets would otherwise sit a hand's width apart with the content in the middle.
 */
@Composable
internal fun BottomNavigation(state: Navigation) {
    val strings = LocalStrings.current

    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        NavigationBar(
            modifier = Modifier.testTag(NAV_BAR_TEST_TAG).widthIn(max = ContentMaxWidth),
            containerColor = Color.Transparent,
            contentColor = MaterialTheme.colorScheme.onBackground,
        ) {
            for (tab in Tab.entries) {
                NavigationBarItem(
                    selected = tab == state.current,
                    onClick = { state.onSelect(tab) },
                    modifier = Modifier.testTag(navTestTag(tab.name.lowercase())),
                    icon = { Icon(imageVector = tab.icon, contentDescription = null) },
                    label = { Text(text = strings[tab.labelKey], maxLines = 1) },
                    colors = NavigationBarItemDefaults.colors(
                        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                        selectedTextColor = MaterialTheme.colorScheme.onBackground,
                        indicatorColor = MaterialTheme.colorScheme.primary,
                        unselectedIconColor =
                        MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                        unselectedTextColor =
                        MaterialTheme.colorScheme.onBackground.copy(alpha = MUTED),
                    ),
                )
            }
        }
    }
}
