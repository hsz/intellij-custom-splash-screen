package info.chrzanowski.customsplashscreen

import com.intellij.util.xmlb.XmlSerializer
import org.jdom.Element
import org.junit.Assert.assertEquals
import org.junit.Test

class SplashScreenSettingsTest {

    @Test
    fun `bundled image selection survives serialization alongside the custom path`() {
        val state = SplashScreenSettings.State(
            enabled = true,
            imagePath = "/local/previous.png",
            bundledImage = "splashes/community/image.png",
        )
        val restored = XmlSerializer.deserialize(XmlSerializer.serialize(state), SplashScreenSettings.State::class.java)
        assertEquals(state, restored)
    }

    @Test
    fun `settings saved before bundled images continue to use the custom path`() {
        val oldState = Element("state")
            .addContent(Element("option").setAttribute("name", "enabled").setAttribute("value", "true"))
            .addContent(Element("option").setAttribute("name", "imagePath").setAttribute("value", "/local/old.png"))
        val restored = XmlSerializer.deserialize(oldState, SplashScreenSettings.State::class.java)
        assertEquals(true, restored.enabled)
        assertEquals("/local/old.png", restored.imagePath)
        assertEquals("", restored.bundledImage)
    }
}