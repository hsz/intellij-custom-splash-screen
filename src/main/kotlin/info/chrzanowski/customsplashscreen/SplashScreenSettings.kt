package info.chrzanowski.customsplashscreen

import com.intellij.openapi.components.*

@Service(Service.Level.APP)
@State(
    name = "CustomSplashScreenSettings",
    storages = [Storage(value = "custom-splash.xml", roamingType = RoamingType.DISABLED)],
)
class SplashScreenSettings : PersistentStateComponent<SplashScreenSettings.State> {

    data class State(
        var enabled: Boolean = false,
        var imagePath: String = "",
        var cacheEntries: MutableList<SplashCacheEntry> = mutableListOf(),
    )

    @Volatile
    private var currentState = State()

    override fun getState() = currentState

    override fun loadState(state: State) {
        currentState = state
    }
}

data class SplashCacheEntry(var fileName: String = "", var sha256: String = "")