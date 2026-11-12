package info.chrzanowski.customsplashscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SplashCacheKeyTest {

    @Test
    fun `legacy keys retain their filename format`() {
        val key = SplashCacheKey.installed(0, "/splash.png")
        assertEquals("0-${Integer.toUnsignedString("splash.png".hashCode(), 36)}-hra0hs.ij", key.fileName(2f))
        assertEquals(key.fileName(2f), SplashCacheKey.installed(0, "splash.png").fileName(2f))
    }

    @Test
    fun `v2 keys include build number resource size and resource path`() {
        val name = SplashCacheKey.installedV2("IU-263.7307.200", "splash.png", 1024).fileName(2f)
        assertEquals(name, SplashCacheKey.installedV2("IU-263.7307.200", "/splash.png", 1024).fileName(2f))
        assertNotEquals(name, SplashCacheKey.installedV2("IU-263.7307.201", "splash.png", 1024).fileName(2f))
        assertNotEquals(name, SplashCacheKey.installedV2("IU-263.7307.200", "splash.png", 1025).fileName(2f))
        assertNotEquals(name, SplashCacheKey.installedV2("IU-263.7307.200", "other.png", 1024).fileName(2f))
        assertNotEquals(name, SplashCacheKey.installedV2("IU-263.7307.200", "splash.png", 1024).fileName(1f))
    }

    @Test
    fun `v3 keys use the same hash with the new separator and suffix`() {
        val v2 = SplashCacheKey.installedV2("IU-271.2726", "splash.png", 1024).fileName(2f)
        val v3 = SplashCacheKey.installedV3("IU-271.2726", "splash.png", 1024).fileName(2f)
        assertEquals("${v2.removeSuffix("hra0hs.v2.ij")}_hra0hs.v3.ij", v3)
        assertEquals(v3, SplashCacheKey.installedV3("IU-271.2726", "/splash.png", 1024).fileName(2f))
    }

    @Test
    fun `all cache formats reject invalid scales`() {
        val keys = listOf(
            SplashCacheKey.installed(0, "splash.png"),
            SplashCacheKey.installedV2("IU-263.7307.200", "splash.png", 0),
            SplashCacheKey.installedV3("IU-271.2726", "splash.png", 0),
        )
        for (key in keys) {
            for (scale in listOf(0f, -1f, Float.NaN, Float.POSITIVE_INFINITY)) {
                assertThrows(IllegalArgumentException::class.java) { key.fileName(scale) }
            }
        }
    }
}