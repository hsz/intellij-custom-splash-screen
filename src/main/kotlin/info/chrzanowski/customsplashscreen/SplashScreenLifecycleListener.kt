package info.chrzanowski.customsplashscreen

import com.intellij.ide.AppLifecycleListener
import com.intellij.ide.plugins.DynamicPluginListener
import com.intellij.ide.plugins.IdeaPluginDescriptor
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import java.io.IOException

class SplashScreenLifecycleListener : AppLifecycleListener, DynamicPluginListener {

    override fun welcomeScreenDisplayed() {
        service<SplashScreenService>().scheduleRefresh()
    }

    override fun pluginLoaded(pluginDescriptor: IdeaPluginDescriptor) {
        if (pluginDescriptor.pluginId.idString == "info.chrzanowski.customsplashscreen") {
            service<SplashScreenService>().scheduleRefresh()
        }
    }

    override fun beforePluginUnload(pluginDescriptor: IdeaPluginDescriptor, isUpdate: Boolean) {
        if (pluginDescriptor.pluginId.idString == "info.chrzanowski.customsplashscreen") {
            try {
                service<SplashScreenService>().beforeUnload()
            } catch (error: IOException) {
                thisLogger().warn("Cannot remove custom splash cache entries before unloading", error)
            }
        }
    }
}