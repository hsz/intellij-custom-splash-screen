package info.chrzanowski.customsplashscreen

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.nio.file.Path

class SplashScreenServicePlatformTest : BasePlatformTestCase() {

    fun testCacheKeyMatchesPlatform() {
        val appInfo = ApplicationInfo.getInstance()
        val resourcePath = requireNotNull(appInfo.splashImageUrl).removePrefix("/")
        val method = Class.forName("com.intellij.platform.ide.bootstrap.SplashManagerKt")
            .declaredMethods.single { it.name == "getCacheFile" }
        method.isAccessible = true
        val key = currentSplashKey()

        for (scale in listOf(1f, 1.25f, 1.5f, 1.75f, 2f, 3f)) {
            val result = if (method.parameterTypes[1] == String::class.java) {
                method.invoke(null, scale, resourcePath, appInfo.build.asString())
            } else {
                method.invoke(null, scale, appInfo, resourcePath)
            }
            val platformPath = when (result) {
                is Path -> result
                is Pair<*, *> -> result.first as Path
                else -> error("Unexpected splash cache path: $result")
            }
            assertEquals(platformPath.fileName.toString(), key.fileName(scale))
        }
    }
}