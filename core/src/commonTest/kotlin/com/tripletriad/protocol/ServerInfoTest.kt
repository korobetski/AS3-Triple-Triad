package com.tripletriad.protocol

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * What a server says about itself.
 *
 * The tests that matter here are about **who this shape has to survive being read by**: a client
 * too old to be allowed to do anything else, and a client newer than the server. Both are ordinary
 * during a rollout, and both would be locked out by the obvious implementations.
 */
class ServerInfoTest {

    @Test
    fun aClientOnTheSameMajorIsAccepted() {
        assertTrue(info(minimumClient = AppVersion(1, 0, 0)).accepts(AppVersion(1, 7, 3)))
    }

    /**
     * A newer client passes, and this is the case that would be got wrong by equality.
     *
     * During any rollout one side ships first. Refusing the players who updated fastest is the
     * wrong way round — the newer side is the one equipped to be careful.
     */
    @Test
    fun aNewerClientIsAccepted() {
        assertTrue(info(minimumClient = AppVersion(1, 0, 0)).accepts(AppVersion(2, 0, 0)))
    }

    @Test
    fun anOlderClientIsNot() {
        assertFalse(info(minimumClient = AppVersion(2, 0, 0)).accepts(AppVersion(1, 9, 9)))
    }

    /**
     * The one property the endpoint's whole purpose depends on: a client that cannot be *served*
     * must still be able to **read this**. So every field a refused client needs — the versions and
     * where to get a new build — has to decode from a body it did not expect to see.
     */
    @Test
    fun anOlderClientCanStillDecodeIt() {
        val fromANewerServer = """
            {
              "name":"eu-1",
              "version":{"major":2,"minor":0,"patch":0},
              "minimumClient":{"major":2,"minor":0,"patch":0},
              "ready":true,
              "release":{
                "version":{"major":2,"minor":0,"patch":0},
                "downloads":{"DESKTOP":"https://example.invalid/tto.msi"},
                "notes":"the card tables changed"
              },
              "somethingThisBuildHasNeverHeardOf":true
            }
        """.trimIndent()

        val info = tolerant.decodeFromString<ServerInfo>(fromANewerServer)

        assertFalse(info.accepts(AppVersion(1, 4, 2)), "this build is the one being refused")
        assertEquals(
            "https://example.invalid/tto.msi",
            info.release?.downloads?.get(ClientPlatform.DESKTOP),
        )
    }

    /**
     * A deployment that publishes nothing is the ordinary case, and must not be a decode failure.
     */
    @Test
    fun aServerWithNoPublishedBuildIsStillReadable() {
        val minimal = """
            {"name":"dev","version":{"major":0,"minor":1,"patch":0},
             "minimumClient":{"major":0,"minor":1,"patch":0}}
        """.trimIndent()

        val info = tolerant.decodeFromString<ServerInfo>(minimal)

        assertNull(info.release)
        assertTrue(info.ready, "readiness defaults to usable rather than to broken")
    }

    /** A download offered for another platform is not one this client may be shown. */
    @Test
    fun downloadsAreKeptApartByPlatform() {
        val release = ClientRelease(
            version = AppVersion(2, 0, 0),
            downloads = mapOf(
                ClientPlatform.ANDROID to "https://example.invalid/store",
                ClientPlatform.DESKTOP to "https://example.invalid/tto.msi",
            ),
        )

        assertNull(release.downloads[ClientPlatform.IOS])
    }

    private fun info(minimumClient: AppVersion) = ServerInfo(
        name = "test",
        version = minimumClient,
        minimumClient = minimumClient,
    )

    private val tolerant = Json { ignoreUnknownKeys = true }
}
