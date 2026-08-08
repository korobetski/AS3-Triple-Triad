package com.tripletriad.data

import org.jetbrains.compose.resources.ExperimentalResourceApi
import tripletriad.shared.generated.resources.Res

/**
 * Reading the two catalogs out of the Compose Multiplatform resource bundle.
 *
 * ### Why these two functions are the whole of what stayed behind
 *
 * When `:core` was extracted so the server could replay matches with the real engine, everything
 * in `model/` moved untouched and almost all of `data/` did too. These few lines are the entire
 * remainder: the *parsers* are pure and moved, but `Res.readBytes` is Compose, and Compose is
 * exactly what a server must not link against.
 *
 * The shape of the split is the point. `:core` knows how to turn text into a catalog and knows
 * nothing about where the text comes from; the client gets it from a resource bundle, and the
 * server will get it from somewhere else entirely without either of them re-parsing differently.
 */

/** Path of the card catalog inside `commonMain/composeResources`. */
const val CARD_CATALOG_PATH: String = "files/cards.json"

/** Path of the NPC catalog inside `commonMain/composeResources`. */
const val NPC_CATALOG_PATH: String = "files/npcs.json"

/** Path of the tournament ladders inside `commonMain/composeResources`. */
const val CAMPAIGN_CATALOG_PATH: String = "files/campaigns.json"

/**
 * Reads and parses `cards.json` out of the Compose Multiplatform resource bundle.
 *
 * Compose resources are the mechanism the migration needs for the 263 card images too, which is
 * why this loads through them rather than through a platform-specific file API.
 */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadCardCatalog(): CardCatalog =
    CardCatalogParser.parse(Res.readBytes(CARD_CATALOG_PATH).decodeToString())

/** Reads and parses `npcs.json` out of the Compose Multiplatform resource bundle. */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadNpcCatalog(): NpcCatalog =
    NpcCatalogParser.parse(Res.readBytes(NPC_CATALOG_PATH).decodeToString())

/** Reads and parses `campaigns.json` out of the Compose Multiplatform resource bundle. */
@OptIn(ExperimentalResourceApi::class)
suspend fun loadCampaignCatalog(): CampaignCatalog =
    CampaignCatalogParser.parse(Res.readBytes(CAMPAIGN_CATALOG_PATH).decodeToString())
