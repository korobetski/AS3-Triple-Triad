package com.tripletriad.protocol

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * The compatibility rule, which is a product decision rather than an implementation detail.
 *
 * The asymmetry is the part worth protecting: it would be very easy, and wrong, to simplify
 * [AppVersion.acceptsPeer] into `major == major` during some future tidy-up. That version would
 * break every player who updated before the server was deployed, which is most of them.
 */
class AppVersionTest {

    // ---- The rule ---------------------------------------------------------

    @Test
    fun aPeerOnTheSameMajorIsAccepted() {
        assertTrue(AppVersion(1, 0, 0).acceptsPeer(AppVersion(1, 9, 9)))
    }

    @Test
    fun anOlderMajorIsRefused() {
        assertFalse(AppVersion(2, 0, 0).acceptsPeer(AppVersion(1, 999, 999)))
    }

    /**
     * The asymmetry, stated as a test so it cannot be tidied away.
     *
     * A client newer than the server is the ordinary state of the world mid-rollout. Refusing it
     * would take the game from the people who updated fastest.
     */
    @Test
    fun aNewerMajorIsAcceptedEvenThoughTheReverseIsNot() {
        val server = AppVersion(1, 0, 0)
        val newerClient = AppVersion(2, 0, 0)

        assertTrue(server.acceptsPeer(newerClient), "a newer peer must not be locked out")
        assertFalse(newerClient.acceptsPeer(server), "and the rule is not symmetric")
    }

    /** Minor and patch promise the replay is unchanged, so they may not affect the decision. */
    @Test
    fun minorAndPatchNeverAffectCompatibility() {
        assertTrue(AppVersion(3, 7, 2).acceptsPeer(AppVersion(3, 0, 0)))
        assertTrue(AppVersion(3, 0, 0).acceptsPeer(AppVersion(3, 7, 2)))
    }

    // ---- Parsing, which reads hostile input -------------------------------

    @Test
    fun aWellFormedVersionRoundTrips() {
        val version = AppVersion(1, 4, 2)

        assertEquals("1.4.2", version.toString())
        assertEquals(version, AppVersion.parse(version.toString()))
    }

    @Test
    fun surroundingWhitespaceIsTolerated() {
        assertEquals(AppVersion(1, 4, 2), AppVersion.parse("  1.4.2 "))
    }

    /**
     * Every one of these arrives from the network, so none of them may throw. A null is a request
     * to refuse the peer; an exception would be a way to take the server down with a header.
     */
    @Test
    fun malformedVersionsParseToNullRatherThanThrowing() {
        listOf("", "1", "1.4", "1.4.2.8", "1.4.x", "a.b.c", "-1.0.0", "1..2", "latest")
            .forEach { assertNull(AppVersion.parse(it), "'$it' should not have parsed") }
    }

    @Test
    fun aNegativeComponentIsRejectedAtConstruction() {
        assertFailsWith<IllegalArgumentException> { AppVersion(1, -1, 0) }
    }

    // ---- Ordering ---------------------------------------------------------

    @Test
    fun versionsOrderByMajorThenMinorThenPatch() {
        val ascending = listOf(
            AppVersion(0, 9, 9),
            AppVersion(1, 0, 0),
            AppVersion(1, 0, 1),
            AppVersion(1, 2, 0),
            AppVersion(2, 0, 0),
        )

        assertEquals(ascending, ascending.shuffled().sorted())
    }

    /**
     * The build says what it is. Asserted so that shipping a `0.0.0` — which
     * [AppVersion.acceptsPeer] would make universally permissive — cannot happen quietly.
     */
    @Test
    fun theCurrentVersionIsSet() {
        assertTrue(CURRENT_VERSION > AppVersion(0, 0, 0), "CURRENT_VERSION is $CURRENT_VERSION")
    }
}
