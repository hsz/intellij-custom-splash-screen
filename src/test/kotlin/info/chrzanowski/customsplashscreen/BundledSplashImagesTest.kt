package info.chrzanowski.customsplashscreen

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream

class BundledSplashImagesTest {

    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `discovers PNGs recursively in resource directories in stable order`() {
        val root = temporaryFolder.newFolder("resources").toPath()
        for (name in listOf("splashes/zebra.PNG", "splashes/community/blue sky.png", "splashes/alpha.png", "splashes/readme.txt", "other.png")) {
            val file = root.resolve(name)
            Files.createDirectories(file.parent)
            Files.write(file, byteArrayOf())
        }
        URLClassLoader(arrayOf(root.toUri().toURL()), null).use { loader ->
            assertEquals(
                listOf("splashes/alpha.png", "splashes/community/blue sky.png", "splashes/zebra.PNG"),
                BundledSplashImages.discover(loader, root.toUri().toURL()),
            )
            Files.write(root.resolve("splashes/new.png"), byteArrayOf())
            assertTrue(BundledSplashImages.discover(loader, null).contains("splashes/new.png"))
        }
    }

    @Test
    fun `discovers packaged PNGs even without JAR directory entries`() {
        val jar = temporaryFolder.newFile("plugin with spaces.jar").toPath()
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            for (name in listOf("splashes/second.PNG", "splashes/community/first.png", "splashes/notes.txt", "elsewhere/image.png")) {
                output.putNextEntry(JarEntry(name))
                output.closeEntry()
            }
        }
        URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { loader ->
            assertEquals(
                listOf("splashes/community/first.png", "splashes/second.PNG"),
                BundledSplashImages.discover(loader, jar.toUri().toURL()),
            )
        }
    }

    @Test
    fun `discovers packaged resources through the classloader and removes duplicates`() {
        val jar = temporaryFolder.newFile("plugin.jar").toPath()
        JarOutputStream(Files.newOutputStream(jar)).use { output ->
            for (name in listOf("splashes/", "splashes/image.png")) {
                output.putNextEntry(JarEntry(name))
                output.closeEntry()
            }
        }
        URLClassLoader(arrayOf(jar.toUri().toURL()), null).use { loader ->
            assertEquals(listOf("splashes/image.png"), BundledSplashImages.discover(loader, null))
            assertEquals(listOf("splashes/image.png"), BundledSplashImages.discover(loader, jar.toUri().toURL()))
        }
    }

    @Test
    fun `missing splash directory produces an empty catalogue`() {
        val root = temporaryFolder.newFolder("empty").toPath()
        URLClassLoader(arrayOf(root.toUri().toURL()), null).use { loader ->
            assertTrue(BundledSplashImages.discover(loader, root.toUri().toURL()).isEmpty())
        }
    }

    @Test
    fun `only PNG resources inside splashes can be selected`() {
        assertTrue(BundledSplashImages.isSplashResource("splashes/community/image.PNG"))
        for (path in listOf("image.png", "/splashes/image.png", "splashes/../image.png", "splashes/./image.png", "splashes//image.png", "splashes/image.jpg")) {
            assertFalse(path, BundledSplashImages.isSplashResource(path))
        }
    }
}