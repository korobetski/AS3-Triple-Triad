package com.tripletriad.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The peer handshake — what two devices exchange so that neither arranges the deal.
 *
 * The tests that matter here are the ones that play the cheat rather than the protocol: a nonce
 * changed after it was committed, a card that was never in the hand, a salt swapped between slots.
 * A commitment scheme that only gets tested with honest inputs is a commitment scheme that has not
 * been tested at all.
 */
class PeerHandshakeTest {

    /** The ordinary case: both sides derive the same seed, and it is the same both ways round. */
    @Test
    fun bothSidesDeriveTheSameSeed() {
        val us = SeedExchange(nonceOf(1))
        val them = SeedExchange(nonceOf(2))

        val ours = us.accept(them.commit, them.reveal())
        val theirs = them.accept(us.commit, us.reveal())

        assertEquals(ours, theirs, "the two devices must agree on the seed")
        assertNotNull(ours)
    }

    /**
     * The seed does not depend on who dialled.
     *
     * `jointSeed` sorts the two nonces by content before hashing, which is what lets both sides
     * reach the same answer without agreeing on which of them is "first" — there is no first.
     */
    @Test
    fun theSeedDoesNotDependOnOrder() {
        val a = SeedExchange(nonceOf(7))
        val b = SeedExchange(nonceOf(9))

        assertEquals(
            a.accept(b.commit, b.reveal()),
            b.accept(a.commit, a.reveal()),
            "a seed that depended on the order would be two different matches",
        )
    }

    /** Different nonces, different match. Otherwise the commitment would be buying nothing. */
    @Test
    fun differentNoncesGiveDifferentSeeds() {
        val fixed = SeedExchange(nonceOf(1))
        val first = SeedExchange(nonceOf(2)).let { fixed.accept(it.commit, it.reveal()) }
        val second = SeedExchange(nonceOf(3)).let { fixed.accept(it.commit, it.reveal()) }

        assertNotEquals(first, second)
    }

    /**
     * **The cheat this exists to stop.** A side that sees the other's nonce and then changes its
     * own — to land the coin flip, or a Random deal it likes — is caught by the hash it already
     * sent, and the match cannot start.
     */
    @Test
    fun aNonceChangedAfterCommittingIsRejected() {
        val honest = SeedExchange(nonceOf(1))
        val cheat = SeedExchange(nonceOf(2))
        val substituted = SeedExchange(nonceOf(3)).reveal()

        assertNull(
            honest.accept(cheat.commit, substituted),
            "a reveal that does not match its commitment must not produce a seed",
        )
    }

    /** Garbage on the wire is refused rather than parsed into something. */
    @Test
    fun aRevealThatIsNotHexIsRejected() {
        val us = SeedExchange(nonceOf(1))
        val them = SeedExchange(nonceOf(2))

        assertNull(us.accept(them.commit, SeedReveal("z".repeat(NONCE_BYTES * 2))))
    }

    /** A commitment is a SHA-256; anything else is not a message this protocol can act on. */
    @Test
    fun aCommitmentOfTheWrongLengthIsRefusedOutright() {
        assertFailsWith<IllegalArgumentException> { SeedCommit("abc") }
        assertFailsWith<IllegalArgumentException> { SeedReveal("ab") }
    }

    /** A hand is five cards. Four or six is not a hand this game can be played with. */
    @Test
    fun aHandIsFiveCards() {
        assertFailsWith<IllegalArgumentException> { commitHand(listOf(1, 2, 3, 4)) }
        assertFailsWith<IllegalArgumentException> { HandCommitment(listOf("a", "b")) }
    }

    /** Every card the player actually holds checks out as it is played. */
    @Test
    fun aCommittedHandAcceptsItsOwnCards() {
        val (commitment, reveals) = commitHand(HAND)

        for (reveal in reveals) {
            assertTrue(commitment.accepts(reveal), "card ${reveal.cardId} was in the hand")
        }
    }

    /** **The second cheat**: a card decided after the board made it convenient. */
    @Test
    fun aCardThatWasNeverInTheHandIsRejected() {
        val (commitment, reveals) = commitHand(HAND)
        val invented = reveals.first().copy(cardId = OUTSIDER)

        assertFalse(commitment.accepts(invented), "a card outside the commitment must not pass")
    }

    /**
     * **The third cheat**: keeping the cards but reordering them, so a card is played from a slot
     * whose turn it was not. Each slot has its own salt, so a reveal only fits where it was made.
     */
    @Test
    fun aCardMovedToAnotherSlotIsRejected() {
        val (commitment, reveals) = commitHand(HAND)
        val moved = reveals.first().copy(slot = 1)

        assertFalse(commitment.accepts(moved), "a reveal only fits the slot it was committed in")
    }

    /** A right card with the wrong salt is a guess, and is refused like one. */
    @Test
    fun aCardWithTheWrongSaltIsRejected() {
        val (commitment, reveals) = commitHand(HAND)
        val resalted = reveals[0].copy(salt = reveals[1].salt)

        assertFalse(commitment.accepts(resalted))
    }

    /**
     * The same hand committed twice looks nothing alike.
     *
     * This is the salt doing its job: a card id is a small number, so unsalted hashes would be a
     * public lookup table and the opponent would read the hand off the wire before a card was
     * played.
     */
    @Test
    fun theSameHandCommitsDifferentlyEveryTime() {
        val (first, _) = commitHand(HAND)
        val (second, _) = commitHand(HAND)

        assertNotEquals(first.slots, second.slots, "two commitments to one hand must not match")
    }

    /** A reveal from one commitment says nothing about another. */
    @Test
    fun aRevealFromAnotherHandIsRejected() {
        val (commitment, _) = commitHand(HAND)
        val (_, otherReveals) = commitHand(HAND)

        assertFalse(commitment.accepts(otherReveals.first()))
    }

    private fun assertNotNull(value: Int?) {
        assertTrue(value != null, "two honest sides must reach a seed")
    }

    private companion object {
        /** A fixed nonce, so a failure names one input rather than a random one. */
        fun nonceOf(fill: Byte) = ByteArray(NONCE_BYTES) { fill }

        val HAND = listOf(1, 3, 6, 7, 10)

        /** A card id not in [HAND]. */
        const val OUTSIDER = 99
    }
}
