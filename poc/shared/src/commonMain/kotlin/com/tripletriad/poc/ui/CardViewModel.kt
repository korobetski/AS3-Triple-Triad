package com.tripletriad.poc.ui

import com.tripletriad.poc.data.Card
import com.tripletriad.poc.data.CardRepository
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlin.coroutines.CoroutineContext

/**
 * ViewModel for managing card state and interactions.
 * Handles loading cards and flip animation state.
 */
class CardViewModel : CoroutineScope {
    override val coroutineContext: CoroutineContext = SupervisorJob()

    private val repository = CardRepository(
        HttpClient(CIO) {
            install(ContentNegotiation) {
                kotlinxSerializationJson(Json { ignoreUnknownKeys = true })
            }
        }
    )

    // State for cards
    private val _cards = MutableStateFlow<List<Card>>(emptyList())
    val cards: StateFlow<List<Card>> = _cards.asStateFlow()

    // State for selected card (for display)
    private val _selectedCard = MutableStateFlow<Card?>(null)
    val selectedCard: StateFlow<Card?> = _selectedCard.asStateFlow()

    // State for card flip animation
    private val _isFlipped = MutableStateFlow(false)
    val isFlipped: StateFlow<Boolean> = _isFlipped.asStateFlow()

    // State for loading status
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // State for error
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    /**
     * Load cards from JSON string.
     */
    fun loadCards(jsonString: String) {
        launch {
            _isLoading.value = true
            _error.value = null
            try {
                val loadedCards = repository.loadCardsFromJson(jsonString)
                _cards.value = loadedCards
                _selectedCard.value = loadedCards.firstOrNull()
            } catch (e: Exception) {
                _error.value = "Failed to load cards: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Select a card by ID.
     */
    fun selectCard(cardId: String) {
        _selectedCard.value = _cards.value.firstOrNull { it.id == cardId }
    }

    /**
     * Select a random card.
     */
    fun selectRandomCard() {
        _selectedCard.value = _cards.value.randomOrNull()
    }

    /**
     * Toggle card flip state.
     */
    fun toggleFlip() {
        _isFlipped.value = !_isFlipped.value
    }

    /**
     * Set flip state explicitly.
     */
    fun setFlipState(flipped: Boolean) {
        _isFlipped.value = flipped
    }

    /**
     * Get the current selected card or a default card if none selected.
     */
    fun getCurrentCard(): Card {
        return _selectedCard.value ?: Card(
            id = "default",
            name = "Default Card",
            image = "",
            top = 1,
            right = 1,
            bottom = 1,
            left = 1
        )
    }

    /**
     * Clear error state.
     */
    fun clearError() {
        _error.value = null
    }
}
