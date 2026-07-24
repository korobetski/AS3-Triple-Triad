package com.tripletriad.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.model.Card
import com.tripletriad.model.CardColor
import com.tripletriad.model.Element

/** Card #001 of the AS3 card list, used as the single sample here. */
val SampleCard = Card(
    id = 1,
    name = "Geezard",
    level = 1,
    top = 1,
    right = 4,
    bottom = 1,
    left = 5,
    element = Element.NONE,
    owner = CardColor.BLUE,
)

private val Backdrop = Color(0xFF14161C)

/** Test tag on the tappable card, used by `shared/src/desktopTest`. */
const val CardTestTag = "flippable-card"

@Composable
fun App() {
    MaterialTheme(colorScheme = darkColorScheme()) {
        Surface(modifier = Modifier.fillMaxSize(), color = Backdrop) {
            var owner by remember { mutableStateOf(SampleCard.owner) }
            var flips by remember { mutableStateOf(0) }

            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Triple Triad — Kotlin Multiplatform PoC",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(28.dp))

                FlippableCard(
                    card = SampleCard,
                    modifier = Modifier.testTag(CardTestTag),
                    onOwnerChanged = {
                        owner = it
                        flips++
                    },
                )

                Spacer(Modifier.height(28.dp))
                Text(
                    text = "Tap the card to flip it.",
                    color = Color.White.copy(alpha = 0.75f),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "owner: ${owner.name.lowercase()}   ·   flips: $flips",
                    color = owner.edge,
                    fontSize = 13.sp,
                )
            }
        }
    }
}
