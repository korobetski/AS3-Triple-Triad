package com.tripletriad.protocol

import kotlinx.serialization.Serializable
import org.kotlincrypto.hash.sha2.SHA256
import org.kotlincrypto.random.CryptoRand

/**
 * What two devices exchange before a peer match, so that neither can arrange the deal.
 *
 * ### The problem this solves
 *
 * A solo transcript is unforgeable because the server recomputes it from a seed
 * ([MatchTranscript]). A peer match has no server present, and the seed decides the coin flip, the
 * roulette's extra rules and — under Random or Three Open — which cards are dealt and which are
 * visible. Whoever picks the seed picks the match. Letting one side pick it, or picking it from
 * whichever clock happens to be ahead, hands that side the game.
 *
 * ### Commit, then reveal
 *
 * Each side draws a nonce it keeps secret and sends only its **hash** ([SeedCommit]). When both
 * hashes are in, both reveal ([SeedReveal]), and each checks the other's nonce against the hash it
 * was given. The seed is derived from the two nonces together ([jointSeed]).
 *
 * Neither side can steer the result, and the reason is worth stating precisely: by the time you
 * learn the opponent's nonce, yours is already fixed by a hash you cannot take back — and a hash
 * you sent before you knew theirs. Choosing your nonce to produce a favourable seed would require
 * predicting a value you had not yet seen.
 *
 * **The order is not negotiable.** A side that reveals before it has the other's commitment has
 * given away the whole scheme: the opponent can then draw a nonce that lands the seed wherever it
 * likes. [SeedExchange] exists so that this ordering lives in one testable place rather than in
 * whatever screen drives the connection.
 *
 * ### What it does not do
 *
 * It does not say who you are talking to. Two honest strangers get a fair seed; an impostor gets an
 * equally fair seed under someone else's name. Identity is signatures, which is the other half of
 * this work and is not here yet — see the phase document.
 */
@Serializable
data class SeedCommit(val hash: String) {
    init {
        require(hash.length == HASH_HEX_LENGTH) {
            "a commitment is a SHA-256 in hex, so ${HASH_HEX_LENGTH} characters, not ${hash.length}"
        }
    }
}

/** The nonce behind a [SeedCommit], sent once both commitments are in. */
@Serializable
data class SeedReveal(val nonce: String) {
    init {
        require(nonce.length == NONCE_HEX_LENGTH) {
            "a nonce is $NONCE_BYTES bytes in hex, not ${nonce.length / 2}"
        }
    }
}

/**
 * One side of the exchange, from its own nonce to the agreed seed.
 *
 * Holds the nonce and hands out only the commitment, which is the whole point: a caller that cannot
 * reach the secret cannot leak it early. [reveal] is deliberately not gated on having received the
 * other commitment — this type cannot know what has arrived over a transport it knows nothing
 * about — so **the caller that drives the connection must not send a reveal before the other
 * side's commitment is in hand**. That obligation moves to the transport when there is one; there
 * is none yet, and it is written here rather than left implied.
 */
class SeedExchange(
    private val nonce: ByteArray = CryptoRand.Default.nextBytes(ByteArray(NONCE_BYTES)),
) {
    init {
        require(nonce.size == NONCE_BYTES) { "a nonce is $NONCE_BYTES bytes, not ${nonce.size}" }
    }

    /** Sent first, and to be sent before anything else. */
    val commit: SeedCommit = SeedCommit(sha256Hex(nonce))

    /** Sent second, once the other side's [SeedCommit] is in hand. */
    fun reveal(): SeedReveal = SeedReveal(nonce.toHex())

    /**
     * Checks the other side's nonce against the commitment it sent, and returns the seed.
     *
     * Null when the two do not match, which means the other side either changed its mind after
     * committing or is not running this protocol. Either way the match cannot start: there is no
     * seed both sides agree on, and playing on would mean playing two different matches.
     */
    fun accept(theirCommit: SeedCommit, theirReveal: SeedReveal): Int? {
        val theirs = theirReveal.nonce.fromHex() ?: return null
        return if (sha256Hex(theirs) == theirCommit.hash) jointSeed(nonce, theirs) else null
    }
}

/**
 * The seed both sides compute, from both nonces.
 *
 * **Sorted before hashing**, so the two devices agree without agreeing on who is "first": each has
 * the same two byte arrays and would otherwise hash them in opposite orders and derive two
 * different matches. Ordering by content rather than by role also means the answer does not depend
 * on which side dialled.
 *
 * The four bytes taken from the digest become the [MatchTranscript.seed] the whole engine runs on.
 * Four rather than more because that is what a `kotlin.random.Random` seed is; the entropy that
 * matters is in the commitment, not in the width of the result.
 */
internal fun jointSeed(a: ByteArray, b: ByteArray): Int {
    val (first, second) = if (a.toHex() <= b.toHex()) a to b else b to a
    val digest = SHA256().digest(first + second)
    return digest.take(SEED_BYTES).fold(0) { acc, byte ->
        (acc shl BITS_PER_BYTE) or byte.toIntBits()
    }
}

/**
 * The five cards a player will bring, stated in a form that reveals nothing until each is played.
 *
 * ### Why the hand has to be committed at all
 *
 * Without it a player can decide what was in their hand *after* seeing the board — there is nothing
 * to contradict a fifth card that becomes whatever the position needs. With it, the five hashes go
 * out before the first placement and every card played has to match one of them.
 *
 * ### Why each card is salted
 *
 * A card id is a small number. The hash of "card 173" is the same on every device in the world, so
 * an unsalted commitment is a lookup table away from being public — the opponent would read the
 * whole hand off the wire. Each slot therefore gets its own random salt, which travels with the
 * card when it is revealed and is worthless before then.
 *
 * Five hashes and no Merkle tree, deliberately: a tree buys short proofs over many leaves, and
 * there are five.
 *
 * @property slots one hash per hand position, in the order the cards will be indexed.
 */
@Serializable
data class HandCommitment(val slots: List<String>) {
    init {
        require(slots.size == HAND_SLOTS) { "a hand is $HAND_SLOTS cards, not ${slots.size}" }
    }

    /**
     * True when [reveal] is the card this commitment promised in that slot.
     *
     * The only check there is: recompute the hash from what was revealed and compare. A false here
     * means the opponent played a card they had not committed to, which is the cheat this whole
     * type exists to catch.
     */
    fun accepts(reveal: CardReveal): Boolean =
        slots.getOrNull(reveal.slot) == cardHash(reveal.cardId, reveal.salt)
}

/**
 * A card, shown as it is played, with the salt that ties it back to [HandCommitment].
 *
 * @property slot which of the five it was.
 * @property cardId the card itself, in the collection both sides agreed on.
 * @property salt the random bytes that were hashed with it. Secret until this message, useless
 *   after it.
 */
@Serializable
data class CardReveal(val slot: Int, val cardId: Int, val salt: String)

/**
 * Commits to [cards], drawing a fresh salt for each.
 *
 * Returns the commitment to send **and** the reveals to keep, because a caller that had to
 * re-derive the salts later would be a caller that stored them somewhere — and a salt written down
 * next to its card is a salt that leaks. Hold the reveals, send them one at a time.
 */
fun commitHand(cards: List<Int>): Pair<HandCommitment, List<CardReveal>> {
    require(cards.size == HAND_SLOTS) { "a hand is $HAND_SLOTS cards, not ${cards.size}" }
    val reveals = cards.mapIndexed { slot, cardId ->
        CardReveal(
            slot = slot,
            cardId = cardId,
            salt = CryptoRand.Default.nextBytes(ByteArray(SALT_BYTES)).toHex(),
        )
    }
    return HandCommitment(reveals.map { cardHash(it.cardId, it.salt) }) to reveals
}

private fun cardHash(cardId: Int, salt: String): String =
    sha256Hex("$cardId:$salt".encodeToByteArray())

private fun sha256Hex(bytes: ByteArray): String = SHA256().digest(bytes).toHex()

private fun ByteArray.toHex(): String = joinToString("") { byte ->
    byte.toIntBits().toString(HEX_RADIX).padStart(2, '0')
}

/** Null on anything that is not an even-length run of hex digits — i.e. on anything not ours. */
private fun String.fromHex(): ByteArray? {
    if (length % 2 != 0) return null
    val values = chunked(2).map { it.toIntOrNull(HEX_RADIX) }
    return if (null in values) null else ByteArray(values.size) { values[it]!!.toByte() }
}

/** A `Byte` is signed in Kotlin; this is the 0..255 the hashing and the hex both want. */
private fun Byte.toIntBits(): Int = toInt() and BYTE_MASK

/** Sixteen bytes: far past guessing, and small enough to send over any transport. */
const val NONCE_BYTES: Int = 16

/** Eight is already beyond a lookup table, and a salt travels with every card played. */
const val SALT_BYTES: Int = 8

private const val HAND_SLOTS = 5
private const val SEED_BYTES = 4
private const val HEX_RADIX = 16
private const val BITS_PER_BYTE = 8
private const val BYTE_MASK = 0xFF
private const val HASH_HEX_LENGTH = 64
private const val NONCE_HEX_LENGTH = NONCE_BYTES * 2
