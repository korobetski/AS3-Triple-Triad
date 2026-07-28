package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.data.CardCatalog
import com.tripletriad.data.loadCardCatalog
import com.tripletriad.i18n.AppLocale
import com.tripletriad.i18n.LocalStrings
import com.tripletriad.i18n.StringKeys
import com.tripletriad.i18n.rememberDeviceLocale
import com.tripletriad.i18n.rememberStrings

private val Backdrop = Color(0xFF14161C)

/** Test tag for the loading/summary line. Match tags live in `MatchScreen.kt`. */
const val CATALOG_SUMMARY_TEST_TAG: String = "catalog-summary"

/**
 * @param locale which of the four string bundles to show. Defaults to the device's, narrowed to
 *   the nearest supported one. It is a parameter and not simply read inside because otherwise
 *   every UI test would assert against whatever language the machine running it happens to be
 *   set to — green on one developer's laptop and red on another's, for no defect. The eventual
 *   settings screen needs the same seam.
 */
@Composable
fun App(locale: AppLocale = rememberDeviceLocale()) {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Backdrop) {
            // `produceState` runs each suspending load once and republishes when it
            // finishes; null is the loading state.
            //
            // Two separate loads because they are separately useful and separately sized:
            // `cards.json` is 60 KB of records, `loadCardArt` is nineteen shared textures
            // (85 KB). The 263 card faces are *not* loaded here — `CardFace` pulls each one
            // as it first needs it, so a match decodes at most nineteen of the seven
            // megabytes on disk. See `CardArt`.
            val catalog by produceState<CardCatalog?>(initialValue = null) {
                value = loadCardCatalog()
            }
            val art by produceState<CardArt?>(initialValue = null) {
                value = loadCardArt()
            }
            // The device language, narrowed to one of the four the original ships, with English
            // behind it. Not gated on either: text renders as its own key until the bundle
            // lands, which is a visible-but-harmless state rather than a blank screen.
            val strings = rememberStrings(locale)

            // No title bar and only a hairline of padding: the board and ten cards want every
            // dp there is. `MatchScreen` measures what it is given rather than being told a
            // size — see `matchLayout`.
            Box(modifier = Modifier.fillMaxSize().padding(4.dp)) {
                val loaded = catalog
                CompositionLocalProvider(LocalStrings provides strings) {
                    if (loaded == null) {
                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                        ) {
                            Text(
                                text = strings[StringKeys.LOADING_CARDS],
                                color = Color.White.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                modifier = Modifier.testTag(CATALOG_SUMMARY_TEST_TAG),
                            )
                        }
                    } else {
                        // Deliberately not gated on `art`: a card composes correctly with no
                        // textures at all — flat colour quad, empty layers — so the board is
                        // playable the instant the records land and fills in as art arrives.
                        CompositionLocalProvider(LocalCardArt provides art) {
                            MatchScreen(catalog = loaded)
                        }
                    }
                }
            }
        }
    }
}
