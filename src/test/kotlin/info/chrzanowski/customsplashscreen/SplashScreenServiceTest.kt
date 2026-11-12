package info.chrzanowski.customsplashscreen

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.impl.ApplicationInfoImpl
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.xml.dom.XmlElement
import org.junit.Assert.assertThrows as assertJUnitThrows
import java.io.IOException

class SplashScreenServiceTest : BasePlatformTestCase() {

    fun testLegacyPlatformsUseLegacyCacheKeys() {
        for (baselineVersion in listOf(261, 262)) {
            val appInfo = applicationInfo(baselineVersion)
            val expected = SplashCacheKey.installed(appInfo.buildDate.toInstant().epochSecond, "missing-splash.png")
            assertEquals(expected.fileName(2f), currentSplashKey(appInfo).fileName(2f))
        }
    }

    fun testV2PlatformsUseV2CacheKeys() {
        for (baselineVersion in listOf(263, 264)) {
            val appInfo = applicationInfo(baselineVersion)
            val expected = SplashCacheKey.installedV2(appInfo.build.asString(), "missing-splash.png", 0)
            assertEquals(expected.fileName(2f), currentSplashKey(appInfo).fileName(2f))
        }
    }

    fun testFuturePlatformsUseLatestCacheKeysWithoutAnUpperBound() {
        for (baselineVersion in listOf(271, 300, 999)) {
            val appInfo = applicationInfo(baselineVersion)
            val expected = SplashCacheKey.installedV3(appInfo.build.asString(), "missing-splash.png", 0)
            assertEquals(expected.fileName(2f), currentSplashKey(appInfo).fileName(2f))
        }
    }

    fun testMissingSplashImageStillReportsAnError() {
        val error = assertJUnitThrows(IOException::class.java) { currentSplashKey(applicationInfo(999, false)) }
        assertEquals(SplashScreenBundle["splash.error.unavailable"], error.message)
    }

    @Suppress("JetBrainsInternalApiUsage")
    private fun applicationInfo(baselineVersion: Int, withSplash: Boolean = true): ApplicationInfo {
        val children = mutableListOf(
            XmlElement("version", mapOf("major" to "2026", "minor" to "3"), emptyList(), null),
            XmlElement("build", mapOf("number" to "IU-$baselineVersion.1", "date" to "20261112"), emptyList(), null),
            XmlElement("icon", mapOf("svg" to "icon.svg", "svg-small" to "icon.svg"), emptyList(), null),
        )
        if (withSplash) children.add(XmlElement("logo", mapOf("url" to "/missing-splash.png"), emptyList(), null))
        return ApplicationInfoImpl(XmlElement("application", emptyMap(), children, null))
    }
}