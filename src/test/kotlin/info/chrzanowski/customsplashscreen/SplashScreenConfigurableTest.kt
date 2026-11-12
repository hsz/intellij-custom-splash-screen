package info.chrzanowski.customsplashscreen

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.ui.TestDialogManager
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.openapi.util.Disposer
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.UIUtil
import java.awt.Color
import java.awt.Dialog
import java.awt.Window
import java.awt.event.FocusEvent
import java.awt.event.KeyEvent
import java.awt.event.MouseEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.nio.file.Path
import java.security.MessageDigest
import javax.imageio.ImageIO
import javax.swing.JButton

class SplashScreenConfigurableTest : BasePlatformTestCase() {

    fun testPreviewDisplaysUnsavedSelectionWithoutModifyingSettings() {
        val settings = service<SplashScreenSettings>()
        val previousState = settings.state
        val configurable = SplashScreenConfigurable()
        try {
            val savedState = previousState.copy(enabled = true, imagePath = "previously-applied.png")
            settings.loadState(savedState)
            val component = configurable.createComponent()
            val pathField = requireNotNull(UIUtil.uiTraverser(component).filter(TextFieldWithBrowseButton::class.java).first())
            val previewButton = UIUtil.uiTraverser(component).filter(JButton::class.java)
                .first { it.text == SplashScreenBundle["splash.settings.preview"] }
            val savedCache = splashCacheSnapshot()

            for (color in listOf(Color.RED, Color.GREEN)) {
                pathField.text = createImage("${color.rgb}.png", color)
                assertTrue(configurable.isModified)

                previewButton.doClick()

                val splash = visibleSplash()
                assertEquals("com.intellij.platform.ide.bootstrap.Splash", splash.javaClass.name)
                val rendered = BufferedImage(splash.width, splash.height, BufferedImage.TYPE_INT_ARGB)
                val graphics = rendered.createGraphics()
                try {
                    splash.paint(graphics)
                } finally {
                    graphics.dispose()
                }
                assertEquals(
                    "Preview must display the unsaved image", color.rgb,
                    rendered.getRGB(rendered.width / 2, rendered.height / 2),
                )
                assertSame(savedState, settings.state)
                assertEquals("previously-applied.png", settings.state.imagePath)
                assertEquals(savedCache, splashCacheSnapshot())
                assertTrue(configurable.isModified)
            }
            configurable.reset()
            assertEquals("previously-applied.png", pathField.text)
            assertFalse(configurable.isModified)
            configurable.disposeUIResources()
            assertSame(savedState, settings.state)
            assertEquals(savedCache, splashCacheSnapshot())
        } finally {
            configurable.disposeUIResources()
            settings.loadState(previousState)
        }
    }

    fun testInvalidPreviewShowsValidationErrorWithoutSavingOrOpeningSplash() {
        val settings = service<SplashScreenSettings>()
        val previousState = settings.state
        val configurable = SplashScreenConfigurable()
        val errors = mutableListOf<String>()
        TestDialogManager.setTestDialog({ message ->
            errors.add(message)
            Messages.OK
        }, testRootDisposable)
        try {
            val savedState = previousState.copy(enabled = true, imagePath = "previously-applied.png")
            settings.loadState(savedState)
            val component = configurable.createComponent()
            val pathField = requireNotNull(UIUtil.uiTraverser(component).filter(TextFieldWithBrowseButton::class.java).first())
            val previewButton = UIUtil.uiTraverser(component).filter(JButton::class.java)
                .first { it.text == SplashScreenBundle["splash.settings.preview"] }
            val missingPath = Path.of("build", "missing-splash-image.png").toAbsolutePath().toString()
            val invalidPath = createImage("invalid", Color.RED)
            Files.writeString(Path.of(invalidPath), "not an image")
            val savedCache = splashCacheSnapshot()

            for ((path, expectedError) in listOf(
                "" to SplashScreenBundle["splash.error.empty"],
                missingPath to SplashScreenBundle["splash.error.unreadable", missingPath],
                invalidPath to SplashScreenBundle["splash.error.format"],
            )) {
                pathField.text = path
                previewButton.doClick()

                assertEquals(expectedError, errors.last())
                assertTrue(Window.getWindows().none { it is Dialog && it.title == "splash" && it.isVisible })
                assertSame(savedState, settings.state)
                assertEquals(savedCache, splashCacheSnapshot())
                assertTrue(configurable.isModified)
            }
            assertEquals(3, errors.size)
        } finally {
            configurable.disposeUIResources()
            settings.loadState(previousState)
        }
    }

    fun testPreviewImageUsesStartupRenderingAndHiDpiSizing() {
        val path = createImage("large-splash", Color.RED, 1440, 960)
        val hiDpi = JreHiDpiUtil.isJreHiDPIEnabled()
        val scale = JBUIScale.scale(1f) * if (hiDpi) JBUIScale.sysScale() else 1f
        val expected = SplashScreenImages.render(SplashScreenImages.load(path), scale)

        val preview = SplashScreenImages.loadPreview(path)

        if (hiDpi) {
            assertEquals((expected.width / scale).toInt(), preview.width)
            assertEquals((expected.height / scale).toInt(), preview.height)
        }
        val rawImage = ImageUtil.toBufferedImage(preview, false)
        assertEquals(expected.width, rawImage.width)
        assertEquals(expected.height, rawImage.height)
        assertTrue(expected.getRGB(0, 0, expected.width, expected.height, null, 0, expected.width).contentEquals(
            rawImage.getRGB(0, 0, rawImage.width, rawImage.height, null, 0, rawImage.width),
        ))
    }

    fun testPreviewIsExcludedFromSettingsModalityAndStaysOnTop() {
        val settings = service<SplashScreenSettings>()
        val previousState = settings.state
        val configurable = SplashScreenConfigurable()
        try {
            settings.loadState(previousState.copy(enabled = true, imagePath = createImage("splash", Color.RED)))
            val component = configurable.createComponent()
            val previewButton = UIUtil.uiTraverser(component).filter(JButton::class.java)
                .first { it.text == SplashScreenBundle["splash.settings.preview"] }

            previewButton.doClick()

            val splash = visibleSplash()
            assertEquals(Dialog.ModalExclusionType.APPLICATION_EXCLUDE, splash.modalExclusionType)
            assertTrue(splash.isAlwaysOnTop)
            assertTrue(splash.focusableWindowState)

            configurable.disposeUIResources()

            assertFalse(splash.isDisplayable)
            assertFalse(splash.isAlwaysOnTop)
        } finally {
            Window.getWindows().filterIsInstance<Dialog>().filter { it.title == "splash" }.forEach { it.dispose() }
            configurable.disposeUIResources()
            settings.loadState(previousState)
        }
    }

    fun testPreviewUsesIdeSplashWithoutModifyingSettings() {
        val settings = service<SplashScreenSettings>()
        val previousState = settings.state
        val configurable = SplashScreenConfigurable()
        try {
            val imagePath = createImage("splash", Color.RED)
            settings.loadState(previousState.copy(enabled = true, imagePath = imagePath))
            val component = configurable.createComponent()
            val previewButton = UIUtil.uiTraverser(component).filter(JButton::class.java)
                .first { it.text == SplashScreenBundle["splash.settings.preview"] }

            assertFalse(configurable.isModified)
            previewButton.doClick()

            val splash = visibleSplash()
            assertEquals("com.intellij.platform.ide.bootstrap.Splash", splash.javaClass.name)
            assertFalse(configurable.isModified)
            assertEquals(imagePath, settings.state.imagePath)
        } finally {
            configurable.disposeUIResources()
            settings.loadState(previousState)
        }
    }

    fun testPreviewIsDisabledWhenCustomSplashIsDisabled() {
        val settings = service<SplashScreenSettings>()
        val previousState = settings.state
        val configurable = SplashScreenConfigurable()
        try {
            settings.loadState(previousState.copy(enabled = false))
            val component = configurable.createComponent()
            val previewButton = UIUtil.uiTraverser(component).filter(JButton::class.java)
                .first { it.text == SplashScreenBundle["splash.settings.preview"] }

            assertFalse(previewButton.isEnabled)
            assertFalse(configurable.isModified)
        } finally {
            configurable.disposeUIResources()
            settings.loadState(previousState)
        }
    }

    fun testNewPreviewDisposesPreviousWindow() {
        val preview = SplashScreenPreview()
        try {
            val image = BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB)
            preview.show(image)
            val first = visibleSplash()

            preview.show(image)
            val second = visibleSplash()

            assertNotSame(first, second)
            assertFalse(first.isDisplayable)
            assertFalse(first.isAlwaysOnTop)
            first.focusListeners.forEach { it.focusLost(FocusEvent(first, FocusEvent.FOCUS_LOST)) }
            assertTrue(second.isVisible)
            assertTrue(second.isAlwaysOnTop)
        } finally {
            preview.dispose()
        }
    }

    fun testMouseInputDisposesPreview() {
        assertDismissedBy { splash ->
            splash.dispatchEvent(MouseEvent(splash, MouseEvent.MOUSE_PRESSED, 0, 0, 1, 1, 1, false))
        }
    }

    fun testKeyInputDisposesPreview() {
        assertDismissedBy { splash ->
            val event = KeyEvent(splash, KeyEvent.KEY_PRESSED, 0, 0, KeyEvent.VK_ESCAPE, KeyEvent.CHAR_UNDEFINED)
            splash.keyListeners.forEach { it.keyPressed(event) }
        }
    }

    fun testFocusLossDisposesPreview() {
        assertDismissedBy { splash ->
            splash.focusListeners.forEach { it.focusLost(FocusEvent(splash, FocusEvent.FOCUS_LOST)) }
        }
    }

    private fun assertDismissedBy(dismiss: (Dialog) -> Unit) {
        val preview = SplashScreenPreview()
        try {
            preview.show(BufferedImage(120, 80, BufferedImage.TYPE_INT_ARGB))
            val splash = visibleSplash()

            dismiss(splash)

            assertFalse(splash.isVisible)
            assertFalse(splash.isDisplayable)
            assertFalse(splash.isAlwaysOnTop)
        } finally {
            preview.dispose()
        }
    }

    private fun createImage(name: String, color: Color, width: Int = 120, height: Int = 80): String {
        val file = Files.createTempFile(Path.of("build"), name, ".png").toAbsolutePath()
        Disposer.register(testRootDisposable) { Files.deleteIfExists(file) }
        val image = BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
        val graphics = image.createGraphics()
        try {
            graphics.color = color
            graphics.fillRect(0, 0, image.width, image.height)
        } finally {
            graphics.dispose()
        }
        ImageIO.write(image, "png", file.toFile())
        return file.toString()
    }

    private fun splashCacheSnapshot(): Map<String, String> {
        val directory = PathManager.getSystemDir().resolve("splash")
        if (!Files.exists(directory)) return emptyMap()
        return Files.list(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) }.toList().associate { path ->
                path.fileName.toString() to MessageDigest.getInstance("SHA-256").digest(Files.readAllBytes(path))
                    .joinToString("") { "%02x".format(it) }
            }
        }
    }

    private fun visibleSplash(): Dialog =
        Window.getWindows().filterIsInstance<Dialog>().single { it.title == "splash" && it.isVisible }
}