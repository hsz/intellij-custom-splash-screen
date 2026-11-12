package info.chrzanowski.customsplashscreen

import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.awt.GraphicsEnvironment
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean

@Service(Service.Level.APP)
class SplashScreenService(private val coroutineScope: CoroutineScope) {

    private val startupScheduled = AtomicBoolean()
    private var unloading = false
    private val settings get() = service<SplashScreenSettings>()
    private val cache get() = SplashScreenCache(PathManager.getSystemDir().resolve("splash"))

    @Synchronized
    fun configure(enabled: Boolean, imagePath: String, bundledImage: String = "") {
        if (unloading) return
        val previous = settings.state
        val images = when {
            enabled -> {
                val source = SplashScreenImages.load(imagePath, bundledImage)
                val key = currentSplashKey()
                splashScales().associate { scale -> key.fileName(scale) to SplashScreenImages.render(source, scale) }
            }

            else -> emptyMap()
        }
        val entries = cache.replace(images, previous.cacheEntries)
        settings.loadState(SplashScreenSettings.State(enabled, imagePath, entries, bundledImage))
    }

    fun scheduleRefresh() {
        val application = ApplicationManager.getApplication()
        if (application.isHeadlessEnvironment || application.isUnitTestMode) return
        if (!startupScheduled.compareAndSet(false, true)) return
        coroutineScope.launch(Dispatchers.IO) {
            synchronized(this@SplashScreenService) {
                if (unloading) return@synchronized
                val state = settings.state
                try {
                    configure(state.enabled, state.imagePath, state.bundledImage)
                } catch (error: IOException) {
                    thisLogger().warn(
                        "Cannot prepare the custom splash image; restoring the default for the next launch",
                        error,
                    )
                    try {
                        restoreDefault()
                    } catch (cleanupError: IOException) {
                        thisLogger().warn("Cannot remove custom splash cache entries", cleanupError)
                    }
                }
            }
        }
    }

    @Synchronized
    fun beforeUnload() {
        unloading = true
        restoreDefault()
    }

    private fun restoreDefault() {
        val state = settings.state
        val entries = cache.replace(emptyMap(), state.cacheEntries)
        settings.loadState(state.copy(cacheEntries = entries))
    }
}

internal fun currentSplashKey(appInfo: ApplicationInfo = ApplicationInfo.getInstance()): SplashCacheKey {
    val resourcePath =
        appInfo.splashImageUrl?.removePrefix("/") ?: throw IOException(SplashScreenBundle["splash.error.unavailable"])
    if (appInfo.build.baselineVersion >= 263) {
        val resourceSize =
            ApplicationInfo::class.java.classLoader.getResourceAsStream(resourcePath)?.use { it.available() } ?: 0
        return if (appInfo.build.baselineVersion >= 271) {
            SplashCacheKey.installedV3(appInfo.build.asString(), resourcePath, resourceSize)
        } else {
            SplashCacheKey.installedV2(appInfo.build.asString(), resourcePath, resourceSize)
        }
    }
    val buildTime =
        appInfo.buildDate?.toInstant()?.epochSecond ?: throw IOException(SplashScreenBundle["splash.error.unavailable"])
    return SplashCacheKey.installed(buildTime, resourcePath)
}

private fun splashScales(): Set<Float> {
    val userScale = JBUIScale.scale(1f)
    if (!JreHiDpiUtil.isJreHiDPIEnabled()) return setOf(userScale)
    val systemScales = mutableSetOf(1f, 1.25f, 1.5f, 1.75f, 2f, JBUIScale.sysScale())
    GraphicsEnvironment.getLocalGraphicsEnvironment().screenDevices.forEach {
        systemScales.add(it.defaultConfiguration.defaultTransform.scaleX.toFloat())
    }
    return systemScales.mapTo(mutableSetOf()) { it * userScale }
}