package info.chrzanowski.customsplashscreen

import com.intellij.ui.JreHiDpiUtil
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.RetinaImage
import java.awt.AlphaComposite
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.file.Files
import java.nio.file.InvalidPathException
import java.nio.file.Path
import javax.imageio.ImageIO
import javax.imageio.stream.MemoryCacheImageInputStream
import kotlin.math.min
import kotlin.math.roundToInt

internal object SplashScreenImages {

    private const val MAX_FILE_SIZE = 20L * 1024 * 1024
    private const val MAX_PIXELS = 16_000_000L

    fun loadPreview(imagePath: String): BufferedImage {
        val hiDpi = JreHiDpiUtil.isJreHiDPIEnabled()
        val scale = JBUIScale.scale(1f) * if (hiDpi) JBUIScale.sysScale() else 1f
        val image = render(load(imagePath), scale)
        return if (hiDpi) {
            RetinaImage.createFrom(image, scale.toDouble(), null) as BufferedImage
        } else {
            image
        }
    }

    fun load(imagePath: String): BufferedImage {
        if (imagePath.isBlank()) throw IOException(SplashScreenBundle["splash.error.empty"])
        val path = try {
            Path.of(imagePath)
        } catch (_: InvalidPathException) {
            throw IOException(SplashScreenBundle["splash.error.path"])
        }
        if (!Files.isRegularFile(path) || !Files.isReadable(path)) {
            throw IOException(SplashScreenBundle["splash.error.unreadable", imagePath])
        }
        if (Files.size(path) > MAX_FILE_SIZE) throw IOException(SplashScreenBundle["splash.error.fileSize"])

        Files.newInputStream(path).use { stream ->
            MemoryCacheImageInputStream(stream).use { input ->
                val readers = ImageIO.getImageReaders(input)
                if (!readers.hasNext()) throw IOException(SplashScreenBundle["splash.error.format"])
                val reader = readers.next()
                try {
                    reader.setInput(input, true, true)
                    val width = reader.getWidth(0)
                    val height = reader.getHeight(0)
                    if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) {
                        throw IOException(SplashScreenBundle["splash.error.dimensions"])
                    }
                    return reader.read(0) ?: throw IOException(SplashScreenBundle["splash.error.format"])
                } finally {
                    reader.dispose()
                }
            }
        }
    }

    @Suppress("UseJBColor", "UndesirableClassUsage")
    fun render(source: BufferedImage, scale: Float): BufferedImage {
        require(scale.isFinite() && scale > 0)
        val fit = min(1.0, min(720.0 / source.width, 480.0 / source.height))
        val width = (source.width * fit * scale).roundToInt().coerceAtLeast(1)
        val height = (source.height * fit * scale).roundToInt().coerceAtLeast(1)
        if (width.toLong() * height > MAX_PIXELS) throw IOException(SplashScreenBundle["splash.error.dimensions"])
        return BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB).apply {
            createGraphics().let { graphics ->
                try {
                    graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON)
                    graphics.setRenderingHint(
                        RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BICUBIC,
                    )
                    graphics.color = Color.WHITE
                    val arc = (16 * scale).roundToInt()
                    graphics.fillRoundRect(0, 0, width, height, arc, arc)
                    graphics.composite = AlphaComposite.SrcIn
                    graphics.drawImage(source, 0, 0, width, height, null)
                } finally {
                    graphics.dispose()
                }
            }
        }
    }
}