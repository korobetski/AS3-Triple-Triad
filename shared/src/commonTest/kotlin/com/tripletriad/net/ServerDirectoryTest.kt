package com.tripletriad.net

import com.tripletriad.storage.InMemoryDocumentStore
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

/**
 * The list of servers, and which one is in play.
 *
 * Two properties are load-bearing and everything here is about one of them: an id derives from the
 * **address** and from nothing else, and the chosen server survives a relaunch. The first is what
 * keeps one server's token off another's requests; the second is what stops a player who chose the
 * second entry from being put back on the first every morning.
 */
class ServerDirectoryTest {

    // ---- Reading a configured list ----------------------------------------

    @Test
    fun aBareAddressIsAServer() {
        val entries = serverEntries("https://eu.example.org")

        assertEquals(1, entries.size)
        assertEquals("https://eu.example.org", entries.first().baseUrl)
    }

    /** The label is what the player sees; the address is what the app uses. */
    @Test
    fun anEntryMayBeLabelled() {
        val entry = serverEntries("Europe=https://eu.example.org").single()

        assertEquals("Europe", entry.label)
        assertEquals("https://eu.example.org", entry.baseUrl)
    }

    /** Without one, the host stands in for it — the scheme is noise on a menu. */
    @Test
    fun anUnlabelledEntryIsNamedAfterItsHost() {
        assertEquals("eu.example.org", serverEntries("https://eu.example.org").single().label)
    }

    @Test
    fun severalAreCommaSeparatedAndKeepTheirOrder() {
        val entries = serverEntries("A=https://a.example.org, B=https://b.example.org")

        assertEquals(listOf("A", "B"), entries.map { it.label })
    }

    @Test
    fun blanksAndStraySpacingAreIgnored() {
        val entries = serverEntries("  ,, A = https://a.example.org ,  ")

        assertEquals(1, entries.size)
        assertEquals("A", entries.single().label)
        assertEquals("https://a.example.org", entries.single().baseUrl)
    }

    @Test
    fun noConfigurationIsNoServers() {
        assertTrue(serverEntries("").isEmpty())
        assertTrue(serverEntries("   ").isEmpty())
    }

    /**
     * One address listed twice is one server.
     *
     * Showing it twice would offer the player a switch that changes nothing — and, because both
     * rows key the same session, one that would look like it had failed.
     */
    @Test
    fun oneAddressUnderTwoLabelsIsStillOneServer() {
        val entries = serverEntries("A=https://a.example.org, Also A=https://a.example.org/")

        assertEquals(1, entries.size)
    }

    // ---- The id is the address --------------------------------------------

    /** Renaming a server must not sign the player out of it. */
    @Test
    fun theIdSurvivesARelabelling() {
        assertEquals(
            ServerEntry.of("https://a.example.org", label = "Europe").id,
            ServerEntry.of("https://a.example.org", label = "EU").id,
        )
    }

    /** A trailing slash is not a different server. */
    @Test
    fun theIdIgnoresATrailingSlash() {
        assertEquals(
            ServerEntry.of("https://a.example.org").id,
            ServerEntry.of("https://a.example.org/").id,
        )
    }

    /** And two addresses are two servers, whatever they are called. */
    @Test
    fun twoAddressesAreTwoServers() {
        assertNotEquals(
            ServerEntry.of("https://a.example.org", label = "Home").id,
            ServerEntry.of("https://b.example.org", label = "Home").id,
        )
    }

    /** The id becomes a filename on both hosts. */
    @Test
    fun theIdIsSafeToUseAsADocumentKey() {
        val id = ServerEntry.of("https://a.example.org:8443/path?x=1").id

        assertTrue(id.all { it.isLetterOrDigit() || it == '_' || it == '-' }, "was $id")
    }

    // ---- Choosing, and remembering the choice -----------------------------

    @Test
    fun theFirstEntryIsTheDefault() {
        val directory = ServerDirectory(InMemoryDocumentStore(), entries)

        assertEquals(entries.first(), directory.selected)
    }

    @Test
    fun aChoiceSurvivesARelaunch() = runTest {
        val store = InMemoryDocumentStore()
        ServerDirectory(store, entries).select(entries[1])

        // A fresh instance over the same storage: the process the player was in has ended.
        val restored = ServerDirectory(store, entries).also { it.restore() }

        assertEquals(entries[1], restored.selected)
    }

    @Test
    fun choosingTheCurrentServerIsNotAChange() = runTest {
        val directory = ServerDirectory(InMemoryDocumentStore(), entries)

        assertFalse(directory.select(entries.first()))
        assertTrue(directory.select(entries[1]))
    }

    /**
     * A server dropped from a build is not a reason to refuse to start.
     *
     * The fallback is visible rather than silent: the servers screen shows the first entry as the
     * selected one, which is the truth.
     */
    @Test
    fun aStoredServerThatIsNoLongerConfiguredFallsBack() = runTest {
        val store = InMemoryDocumentStore()
        ServerDirectory(store, entries).select(entries[1])

        val shorter = listOf(entries.first())
        val directory = ServerDirectory(store, shorter).also { it.restore() }

        assertEquals(entries.first(), directory.selected)
    }

    @Test
    fun nothingStoredLeavesTheDefaultInPlace() = runTest {
        val directory = ServerDirectory(InMemoryDocumentStore(), entries).also { it.restore() }

        assertEquals(entries.first(), directory.selected)
    }

    /** An unreadable choice costs the choice, not the launch. */
    @Test
    fun anUnreadableStoreLeavesAWorkingDefault() = runTest {
        val store = InMemoryDocumentStore(failure = IllegalStateException("permission denied"))

        val directory = ServerDirectory(store, entries)
        directory.restore()
        directory.select(entries[1])

        assertEquals(entries[1], directory.selected)
    }

    /** Selecting something that is not on the list is a configuration bug, not a runtime state. */
    @Test
    fun anUnconfiguredServerCannotBeSelected() = runTest {
        val directory = ServerDirectory(InMemoryDocumentStore(), listOf(entries.first()))

        assertFailsWith<IllegalArgumentException> { directory.select(entries[1]) }
    }

    /** "No server" is expressed by there being no directory, not by an empty one. */
    @Test
    fun aDirectoryWithNoServersIsRefused() {
        assertFailsWith<IllegalArgumentException> {
            ServerDirectory(InMemoryDocumentStore(), emptyList())
        }
    }

    private val entries = serverEntries("A=https://a.example.org, B=https://b.example.org")
}
