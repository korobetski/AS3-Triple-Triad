package com.tripletriad.data

import com.tripletriad.model.Card
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * The two card tables of `sources/src/tto/datas/cards.as`, as extracted by
 * `kotlin/tools/extract_cards.py`.
 *
 * The AS3 picks one at runtime with
 * `cards[String(Game.PROFILE_DATAS.MODE).toUpperCase() + "DATAS"]`, so a profile is
 * in exactly one collection at a time; both are shipped and selected the same way
 * here.
 */
@Serializable
data class CardCatalog(
    val ff14: List<Card>,
    val ff8: List<Card>,
) {
    /** All cards of both collections, ff14 first, in AS3 array order. */
    val all: List<Card> get() = ff14 + ff8

    /**
     * The cards of one collection, keyed the way the AS3 keys them: `"ff14_"` /
     * `"ff8_"`, the texture-name prefix.
     */
    fun collection(prefix: String): List<Card> = when (prefix) {
        "ff14_" -> ff14
        "ff8_" -> ff8
        else -> throw IllegalArgumentException("unknown collection '$prefix'")
    }
}

/**
 * Parses the card catalog.
 *
 * Split from the loader — which lives in `:shared`, because reading the bytes needs Compose
 * resources — so that this module stays free of any way to obtain them. The server has the same
 * catalog to parse and a completely different way of getting hold of it.
 */
object CardCatalogParser {
    // The extractor emits every field, but being lenient about unknown keys means a
    // later field addition does not break older clients.
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CardCatalog = json.decodeFromString(text)
}
