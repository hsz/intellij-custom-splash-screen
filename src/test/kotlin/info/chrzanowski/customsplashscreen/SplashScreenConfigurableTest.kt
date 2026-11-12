package info.chrzanowski.customsplashscreen

import com.intellij.openapi.components.service
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.testFramework.LightPlatformTestCase
import com.intellij.ui.components.JBCheckBox
import com.intellij.ui.components.JBList
import java.awt.Component
import java.awt.Container
import javax.swing.JButton
import javax.swing.JRadioButton

class SplashScreenConfigurableTest : LightPlatformTestCase() {

    private lateinit var configurable: SplashScreenConfigurable

    override fun setUp() {
        super.setUp()
        configurable = SplashScreenConfigurable()
    }

    override fun tearDown() {
        try {
            configurable.disposeUIResources()
            service<SplashScreenSettings>().loadState(SplashScreenSettings.State())
        } finally {
            super.tearDown()
        }
    }

    fun testResetRestoresBundledSelectionAndEnabledState() {
        service<SplashScreenSettings>().loadState(
            SplashScreenSettings.State(enabled = true, imagePath = "custom.png", bundledImage = "splashes/missing.png"),
        )
        val controls = descendants(configurable.createComponent())
        val enabled = controls.filterIsInstance<JBCheckBox>().single()
        val path = controls.filterIsInstance<TextFieldWithBrowseButton>().single()
        val gallery = controls.filterIsInstance<JBList<*>>().single()
        val custom = controls.filterIsInstance<JRadioButton>().single { it.text == SplashScreenBundle["splash.settings.custom"] }
        val preview = controls.filterIsInstance<JButton>().single { it.text == SplashScreenBundle["splash.settings.preview"] }

        assertEquals("splashes/missing.png", gallery.selectedValue)
        assertTrue(gallery.isEnabled)
        assertFalse(path.isEnabled)
        assertFalse(configurable.isModified)

        custom.doClick()
        assertTrue(path.isEnabled)
        assertFalse(gallery.isEnabled)
        assertTrue(configurable.isModified)
        path.text = "changed.png"

        configurable.reset()
        assertEquals("custom.png", path.text)
        assertTrue(gallery.isEnabled)
        assertFalse(path.isEnabled)
        assertFalse(configurable.isModified)

        enabled.doClick()
        assertFalse(path.isEnabled)
        assertFalse(gallery.isEnabled)
        assertFalse(preview.isEnabled)
        configurable.reset()
        assertTrue(preview.isEnabled)
        assertFalse(configurable.isModified)
    }

    fun testDisabledSourceSelectionCanBeAppliedAndRestored() {
        service<SplashScreenSettings>().loadState(
            SplashScreenSettings.State(imagePath = "custom.png", bundledImage = "splashes/missing.png"),
        )
        val controls = descendants(configurable.createComponent())
        val custom = controls.filterIsInstance<JRadioButton>().single { it.text == SplashScreenBundle["splash.settings.custom"] }
        val bundled = controls.filterIsInstance<JRadioButton>().single { it.text == SplashScreenBundle["splash.settings.bundled"] }
        custom.isSelected = true
        assertTrue(configurable.isModified)
        configurable.apply()
        assertEquals("", service<SplashScreenSettings>().state.bundledImage)
        assertEquals("custom.png", service<SplashScreenSettings>().state.imagePath)
        assertFalse(configurable.isModified)

        bundled.isSelected = true
        configurable.apply()
        assertEquals("splashes/missing.png", service<SplashScreenSettings>().state.bundledImage)
        assertFalse(configurable.isModified)
        configurable.reset()
        assertTrue(bundled.isSelected)
    }

    fun testExistingCustomPathRemainsSelected() {
        service<SplashScreenSettings>().loadState(SplashScreenSettings.State(imagePath = "/local/custom.png"))
        val controls = descendants(configurable.createComponent())
        val path = controls.filterIsInstance<TextFieldWithBrowseButton>().single()
        val custom = controls.filterIsInstance<JRadioButton>().single { it.text == SplashScreenBundle["splash.settings.custom"] }
        assertEquals("/local/custom.png", path.text)
        assertTrue(custom.isSelected)
        assertFalse(configurable.isModified)
    }

    private fun descendants(component: Component): List<Component> =
        listOf(component) + if (component is Container) component.components.flatMap(::descendants) else emptyList()
}