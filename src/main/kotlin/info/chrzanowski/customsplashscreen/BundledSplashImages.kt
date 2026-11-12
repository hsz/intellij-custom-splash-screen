package info.chrzanowski.customsplashscreen

import java.net.JarURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.Path
import java.util.jar.JarFile

internal object BundledSplashImages {

    fun discover(
        classLoader: ClassLoader = javaClass.classLoader,
        codeSource: URL? = javaClass.protectionDomain.codeSource?.location,
    ): List<String> {
        val images = sortedSetOf<String>()
        val roots = classLoader.getResources("splashes")
        while (roots.hasMoreElements()) {
            val root = roots.nextElement()
            when (root.protocol) {
                "file" -> scanDirectory(Path.of(root.toURI()), images)
                "jar" -> {
                    val connection = root.toURI().toURL().openConnection() as JarURLConnection
                    connection.useCaches = false
                    connection.jarFile.use { scanJar(it, images) }
                }
            }
        }
        if (codeSource?.protocol == "file") {
            val path = Path.of(codeSource.toURI())
            when {
                Files.isDirectory(path) -> scanDirectory(path.resolve("splashes"), images)
                Files.isRegularFile(path) -> JarFile(path.toFile()).use { scanJar(it, images) }
            }
        }
        return images.toList()
    }

    fun isSplashResource(path: String): Boolean =
        path.startsWith("splashes/") && path.endsWith(".png", ignoreCase = true) &&
            path.split('/').none { it.isEmpty() || it == "." || it == ".." }

    private fun scanDirectory(directory: Path, images: MutableSet<String>) {
        if (!Files.isDirectory(directory)) return
        Files.walk(directory).use { paths ->
            paths.filter { Files.isRegularFile(it) }.forEach { path ->
                val resource = "splashes/" + directory.relativize(path).joinToString("/")
                if (isSplashResource(resource)) images.add(resource)
            }
        }
    }

    private fun scanJar(jar: JarFile, images: MutableSet<String>) {
        jar.entries().asSequence().filter { !it.isDirectory && isSplashResource(it.name) }
            .forEach { images.add(it.name) }
    }
}