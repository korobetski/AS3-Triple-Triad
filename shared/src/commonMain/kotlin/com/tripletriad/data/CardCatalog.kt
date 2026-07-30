package com.tripletriad.data

import com.tripletriad.model.Card
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.compose.resources.ExperimentalResourceApi
import tripletriad.shared.generated.resources.Res

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
 * Parses the card catalog. Split out from [loadCardCatalog] so it can be tested in
 * `commonTest` without a resource loader or a Compose environment.
 */
object CardCatalogParser {
    // The extractor emits every field, but being lenient about unknown keys means a
    // later field addition does not break older clients.
    private val json = Json { ignoreUnknownKeys = true }

    fun parse(text: String): CardCatalog = json.decodeFromString(text)
}

/** Path of the catalog inside `commonMain/composeResources`. */
const val CARD_CATALOG_PATH: String = "files/cards.json"

/**
 * Reads and parses `cards.json` out of the Compose Multiplatform resource bundle.
 *
 * Compose resources are the mechanism the real migration needs for the 263 card
 * images too, which is why the PoC loads through them rather than through a
 * platform-specific file API.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadCardCatalog(): CardCatalog =
    CardCatalogParser.parse(Res.readBytes(CARD_CATALOG_PATH).decodeToString())
