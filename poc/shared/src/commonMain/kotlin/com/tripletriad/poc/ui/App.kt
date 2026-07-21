package com.tripletriad.poc.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.ExperimentalAnimationApi
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tripletriad.poc.data.Card
import com.tripletriad.poc.data.Element

/**
 * Main app component for the Triple Triad PoC.
 * Displays a card with flip animation and touch interaction.
 */
@OptIn(ExperimentalAnimationApi::class)
@Composable
fun App() {
    val viewModel = remember { CardViewModel() }
    val cards by viewModel.cards.collectAsState()
    val selectedCard by viewModel.selectedCard.collectAsState()
    val isFlipped by viewModel.isFlipped.collectAsState()
    val isLoading by viewModel.isLoading.collectAsState()
    val error by viewModel.error.collectAsState()
    
    // Sample card data (will be loaded from JSON in production)
    val sampleJson = """
        [
            {
                "id": "squall",
                "name": "Squall",
                "image": "squall",
                "top": 8,
                "right": 5,
                "bottom": 6,
                "left": 3,
                "element": "FIRE",
                "rarity": "RARE"
            },
            {
                "id": "cloud",
                "name": "Cloud",
                "image": "cloud",
                "top": 9,
                "right": 7,
                "bottom": 4,
                "left": 5,
                "element": "LIGHTNING",
                "rarity": "LEGENDARY"
            },
            {
                "id": "tifa",
                "name": "Tifa",
                "image": "tifa",
                "top": 6,
                "right": 8,
                "bottom": 7,
                "left": 4,
                "element": "EARTH",
                "rarity": "RARE"
            }
        ]
    """.trimIndent()

    // Load cards on startup
    LaunchedEffect(Unit) {
        viewModel.loadCards(sampleJson)
    }

    MaterialTheme {
        Surface(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(16.dp)
            ) {
                // Content based on state
                when {
                    isLoading -> {
                        LoadingScreen()
                    }
                    error != null -> {
                        ErrorScreen(error = error ?: "Unknown error") {
                            viewModel.clearError()
                            viewModel.loadCards(sampleJson)
                        }
                    }
                    else -> {
                        CardScreen(
                            card = selectedCard,
                            isFlipped = isFlipped,
                            onCardClick = { viewModel.toggleFlip() },
                            onRandomCard = { viewModel.selectRandomCard() },
                            onPreviousCard = { 
                                val currentIndex = cards.indexOfFirst { it.id == selectedCard?.id }
                                if (currentIndex > 0) {
                                    viewModel.selectCard(cards[currentIndex - 1].id)
                                }
                            },
                            onNextCard = { 
                                val currentIndex = cards.indexOfFirst { it.id == selectedCard?.id }
                                if (currentIndex < cards.size - 1) {
                                    viewModel.selectCard(cards[currentIndex + 1].id)
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun LoadingScreen() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            CircularProgressIndicator(color = Color.White)
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = "Loading cards...",
                color = Color.White,
                fontSize = 16.sp
            )
        }
    }
}

@Composable
fun ErrorScreen(error: String, onRetry: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Error: $error",
                color = Color.Red,
                fontSize = 16.sp,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = onRetry,
                modifier = Modifier.padding(horizontal = 32.dp)
            ) {
                Text("Retry")
            }
        }
    }
}

@OptIn(ExperimentalAnimationApi::class)
@Composable
fun CardScreen(
    card: Card?,
    isFlipped: Boolean,
    onCardClick: () -> Unit,
    onRandomCard: () -> Unit,
    onPreviousCard: () -> Unit,
    onNextCard: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        // Header
        Text(
            text = "Triple Triad PoC",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 32.dp)
        )

        // Card display area
        Box(
            modifier = Modifier
                .size(300.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(Color.DarkGray)
                .clickable(onClick = onCardClick)
                .pointerInput(Unit) {
                    detectTapGestures(
                        onTap = { onCardClick() },
                        onLongPress = { onRandomCard() }
                    )
                },
            contentAlignment = Alignment.Center
        ) {
            card?.let { c ->
                AnimatedContent(
                    targetState = isFlipped,
                    transitionSpec = {
                        if (targetState) {
                            (fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { height -> height / 2 })
                                .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { height -> -height / 2 })
                        } else {
                            (fadeIn(animationSpec = tween(300)) + slideInVertically(animationSpec = tween(300)) { height -> -height / 2 })
                                .togetherWith(fadeOut(animationSpec = tween(300)) + slideOutVertically(animationSpec = tween(300)) { height -> height / 2 })
                        }
                    },
                    label = "card_flip"
                ) { flipped ->
                    if (flipped) {
                        CardBack()
                    } else {
                        CardFront(card = c)
                    }
                }
            } ?: run {
                Text(
                    text = "No card selected",
                    color = Color.White,
                    fontSize = 16.sp
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card info
        card?.let { c ->
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = c.name,
                    color = Color.White,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Power: ${c.power}",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Top: ${c.top} | Right: ${c.right} | Bottom: ${c.bottom} | Left: ${c.left}",
                    color = Color.LightGray,
                    fontSize = 14.sp
                )
                c.element?.let { element ->
                    Text(
                        text = "Element: ${element.name}",
                        color = getElementColor(element),
                        fontSize = 14.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Control buttons
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onRandomCard,
                modifier = Modifier.fillMaxWidth(0.5f)
            ) {
                Text("Random Card")
            }
            
            Row(
                modifier = Modifier.fillMaxWidth(0.7f),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = onPreviousCard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Previous")
                }
                Button(
                    onClick = onNextCard,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Next")
                }
            }
        }
    }
}

@Composable
fun getElementColor(element: Element): Color {
    return when (element) {
        Element.FIRE -> Color.Red
        Element.ICE -> Color.Cyan
        Element.LIGHTNING -> Color.Yellow
        Element.EARTH -> Color.Green
        Element.WIND -> Color.White
        Element.WATER -> Color.Blue
        Element.LIGHT -> Color(0xFFFFFFFF)
        Element.DARK -> Color(0xFF800080)
        Element.NONE -> Color.Gray
    }
}

@Composable
fun CardFront(card: Card) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .background(getElementColor(card.element ?: Element.NONE))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = card.top.toString(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = card.left.toString(),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = card.name,
                    color = Color.Black,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = card.right.toString(),
                    color = Color.White,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            
            Text(
                text = card.bottom.toString(),
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun CardBack() {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .aspectRatio(1f)
            .background(Color.Blue),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "TRIPLE TRIAD",
            color = Color.White,
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center
        )
    }
}
