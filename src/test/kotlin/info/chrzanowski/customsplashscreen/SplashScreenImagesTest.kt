package info.chrzanowski.customsplashscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.awt.image.BufferedImage
import java.io.IOException
import java.net.URLClassLoader
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import javax.imageio.ImageIO

class SplashScreenImagesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `bundled resource loads without a local path and takes precedence over it`() {
        val root = temporaryFolder.newFolder("resources").toPath()
        val image = createImage(root.resolve("splashes/community/blue sky.png"))
        URLClassLoader(arrayOf(root.toUri().toURL()), null).use { loader ->
            val loaded = SplashScreenImages.load("not a local file", "splashes/community/blue sky.png", loader)
            assertEquals(image.width, loaded.width)
            assertEquals(image.height, loaded.height)
            assertEquals(image.getRGB(0, 0), loaded.getRGB(0, 0))
        }
    }

    @Test
    fun `packaged image can be discovered and loaded directly from the JAR`() {
        val root = temporaryFolder.newFolder("source").toPath()
        val imagePath = root.resolve("image.png")
        val image = createImage(imagePath)
        val jar = temporaryFolder.newFile("plugin.jar").toPath()
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            output.putNextEntry(JarEntry("splashes/image.png"))
            Files.copy(imagePath, output)
            output.closeEntry()
        }
        URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { loader ->
            val resource = BundledSplashImages.discover(loader, jar.toUri().toURL()).single()
            val loaded = SplashScreenImages.load("", resource, loader)
            assertEquals(image.width, loaded.width)
            assertEquals(image.getRGB(0, 0), loaded.getRGB(0, 0))
        }
    }

    @Test
    fun `custom files still load when no bundled image is selected`() {
        val path = temporaryFolder.newFile("custom.png").toPath()
        val image = createImage(path)
        val loaded = SplashScreenImages.load(path.toString())
        assertEquals(image.width, loaded.width)
        assertEquals(image.getRGB(0, 0), loaded.getRGB(0, 0))
    }

    @Test
    fun `missing or invalid bundled resources do not fall back to a custom file`() {
        val path = temporaryFolder.newFile("custom.png").toPath()
        createImage(path)
        for (resource in listOf("splashes/missing.png", "splashes/../custom.png", "outside.png")) {
            assertThrows(IOException::class.java) { SplashScreenImages.load(path.toString(), resource) }
        }
    }

    @Test
    fun `corrupt bundled PNG reports an image error`() {
        val root = temporaryFolder.newFolder("corrupt").toPath()
        Files.createDirectories(root.resolve("splashes"))
        Files.writeString(root.resolve("splashes/broken.png"), "not an image")
        URLClassLoader(arrayOf(root.toUri().toURL()), null).use { loader ->
            assertThrows(IOException::class.java) { SplashScreenImages.load("", "splashes/broken.png", loader) }
        }
    }

    @Test
    fun `bundled resources obey the file size limit`() {
        val root = temporaryFolder.newFolder("oversized").toPath()
        Files.createDirectories(root.resolve("splashes"))
        Files.write(root.resolve("splashes/large.png"), ByteArray(20 * 1024 * 1024 + 1))
        URLClassLoader(arrayOf(root.toUri().toURL()), null).use { loader ->
            assertThrows(IOException::class.java) { SplashScreenImages.load("", "splashes/large.png", loader) }
        }
    }

    private fun createImage(path: Path): BufferedImage {
        Files.createDirectories(path.parent)
        return BufferedImage(4, 3, BufferedImage.TYPE_INT_ARGB).apply {
            setRGB(0, 0, 0xff336699.toInt())
            ImageIO.write(this, "png", path.toFile())
        }
    }
}