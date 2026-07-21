package com.tripletriad.poc.data

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

/**
 * Repository for loading and managing card data.
 * Supports both local JSON loading and remote API fetching.
 */
class CardRepository(private val httpClient: HttpClient) {

    private val json = Json { ignoreUnknownKeys = true }

    /**
     * Load cards from local JSON resource.
     */
    suspend fun loadCardsFromJson(jsonString: String): List<Card> {
        return withContext(Dispatchers.IO) {
            json.decodeFromString<List<Card>>(jsonString)
        }
    }

    /**
     * Load cards from a remote URL.
     */
    suspend fun loadCardsFromUrl(url: String): Result<List<Card>> {
        return runCatching {
            withContext(Dispatchers.IO) {
                val response = httpClient.get(url)
                if (response.status == HttpStatusCode.OK) {
                    val jsonString = response.body<String>()
                    json.decodeFromString<List<Card>>(jsonString)
                } else {
                    throw Exception("Failed to load cards: HTTP ${response.status}")
                }
            }
        }
    }

    /**
     * Get a specific card by ID.
     */
    fun getCardById(cards: List<Card>, id: String): Card? {
        return cards.firstOrNull { it.id == id }
    }

    /**
     * Filter cards by element.
     */
    fun getCardsByElement(cards: List<Card>, element: Element): List<Card> {
        return cards.filter { it.element == element }
    }

    /**
     * Filter cards by rarity.
     */
    fun getCardsByRarity(cards: List<Card>, rarity: Rarity): List<Card> {
        return cards.filter { it.rarity == rarity }
    }

    /**
     * Get random card from the list.
     */
    fun getRandomCard(cards: List<Card>): Card? {
        return cards.randomOrNull()
    }

    /**
     * Search cards by name.
     */
    fun searchCards(cards: List<Card>, query: String): List<Card> {
        return cards.filter { 
            it.name.contains(query, ignoreCase = true) 
        }
    }
}
