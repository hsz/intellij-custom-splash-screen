package info.chrzanowski.customsplashscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.Files

class SplashScreenCacheTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `legacy cache entries can be written and restored`() {
        assertCacheRoundTrip("tk5hrc-rylvvo-hra0hs.ij")
    }

    @Test
    fun `v2 cache entries can be written and restored`() {
        assertCacheRoundTrip("16r23a83eex7phra0hs.v2.ij")
    }

    @Test
    fun `v3 cache entries can be written and restored`() {
        assertCacheRoundTrip("16r23a83eex7p_hra0hs.v3.ij")
    }

    @Test
    fun `upgrading to v2 removes owned legacy entries`() {
        val directory = temporaryFolder.newFolder().toPath()
        val cache = SplashScreenCache(directory)
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB)
        val legacyName = "tk5hrc-rylvvo-hra0hs.ij"
        val currentName = "16r23a83eex7phra0hs.v2.ij"
        val legacyEntries = cache.replace(mapOf(legacyName to image), emptyList())

        val entries = cache.replace(mapOf(currentName to image), legacyEntries)

        assertFalse(Files.exists(directory.resolve(legacyName)))
        assertTrue(Files.exists(directory.resolve(currentName)))
        assertEquals(currentName, entries.single().fileName)
    }

    @Test
    fun `upgrading to v3 removes owned v2 entries`() {
        val directory = temporaryFolder.newFolder().toPath()
        val cache = SplashScreenCache(directory)
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB)
        val previousName = "16r23a83eex7phra0hs.v2.ij"
        val currentName = "16r23a83eex7p_hra0hs.v3.ij"
        val previousEntries = cache.replace(mapOf(previousName to image), emptyList())

        val entries = cache.replace(mapOf(currentName to image), previousEntries)

        assertFalse(Files.exists(directory.resolve(previousName)))
        assertTrue(Files.exists(directory.resolve(currentName)))
        assertEquals(currentName, entries.single().fileName)
    }

    @Test
    fun `entries modified by the IDE are not removed`() {
        val directory = temporaryFolder.newFolder().toPath()
        val cache = SplashScreenCache(directory)
        for (name in listOf("16r23a83eex7phra0hs.v2.ij", "16r23a83eex7p_hra0hs.v3.ij")) {
            val entries = cache.replace(mapOf(name to BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB)), emptyList())
            Files.writeString(directory.resolve(name), "replaced by the IDE")

            cache.replace(emptyMap(), entries)

            assertEquals("replaced by the IDE", Files.readString(directory.resolve(name)))
        }
    }

    @Test
    fun `unsafe or unknown cache file names are rejected`() {
        val cache = SplashScreenCache(temporaryFolder.newFolder().toPath())
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB)
        val unsafeNames = listOf(
            "../splash.v2.ij", "/splash.v2.ij", "splash.v3.ij", "splash.v2.ij.bak", "../hash_scale.v3.ij", "hash_scale.v4.ij",
        )
        for (name in unsafeNames) {
            assertThrows(IllegalArgumentException::class.java) { cache.replace(mapOf(name to image), emptyList()) }
        }
    }

    private fun assertCacheRoundTrip(name: String) {
        val directory = temporaryFolder.newFolder().toPath()
        val cache = SplashScreenCache(directory)
        val image = BufferedImage(2, 1, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xff336699.toInt())
            setRGB(1, 0, 0xff112233.toInt())
        }

        val entries = cache.replace(mapOf(name to image), emptyList())

        assertEquals(name, entries.single().fileName)
        val buffer = ByteBuffer.wrap(Files.readAllBytes(directory.resolve(name))).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals(16, buffer.remaining())
        assertEquals(2, buffer.int)
        assertEquals(1, buffer.int)
        assertEquals(image.getRGB(0, 0), buffer.int)
        assertEquals(image.getRGB(1, 0), buffer.int)

        assertTrue(cache.replace(emptyMap(), entries).isEmpty())
        assertFalse(Files.exists(directory.resolve(name)))
    }
}