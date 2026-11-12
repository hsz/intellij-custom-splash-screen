package info.chrzanowski.customsplashscreen

import java.awt.image.BufferedImage
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.*

internal class SplashCacheKey private constructor(private val prefix: String) {

    fun fileName(scale: Float): String {
        require(scale.isFinite() && scale > 0)
        return "$prefix-${Integer.toUnsignedString(scale.toBits(), 36)}.ij"
    }

    companion object {
        fun installed(buildTime: Long, resourcePath: String): SplashCacheKey {
            val buildId = java.lang.Long.toUnsignedString(buildTime, 36)
            val resourceId = Integer.toUnsignedString(resourcePath.removePrefix("/").hashCode(), 36)
            return SplashCacheKey("$buildId-$resourceId")
        }
    }
}

internal class SplashScreenCache(
    private val directory: Path,
    private val moveFile: (Path, Path) -> Unit = ::moveCacheFile,
) {

    private data class Change(
        val target: Path,
        val entry: SplashCacheEntry?,
        var staged: Path? = null,
        var backup: Path? = null,
        var committed: Boolean = false,
    )

    fun replace(
        images: Map<String, BufferedImage>,
        previousEntries: List<SplashCacheEntry>,
    ): MutableList<SplashCacheEntry> {
        if (images.isEmpty() && previousEntries.isEmpty()) return mutableListOf()
        val changes = mutableListOf<Change>()
        try {
            if (images.isNotEmpty()) Files.createDirectories(directory)
            for ((fileName, image) in images) {
                require(isCacheFileName(fileName))
                val bytes = encode(image)
                val entry = SplashCacheEntry(fileName, digest(bytes))
                val change = Change(directory.resolve(fileName), entry)
                changes.add(change)
                val staged = Files.createTempFile(directory, ".custom-splash-", ".tmp")
                change.staged = staged
                Files.write(staged, bytes)
                backup(change)
            }
            for (entry in previousEntries.distinctBy { it.fileName }) {
                if (entry.fileName !in images && owns(entry)) {
                    val change = Change(directory.resolve(entry.fileName), null)
                    changes.add(change)
                    backup(change)
                }
            }
            for (change in changes) {
                val staged = change.staged
                if (staged == null) Files.deleteIfExists(change.target) else moveFile(staged, change.target)
                change.committed = true
            }
            return changes.mapNotNullTo(mutableListOf()) { it.entry }
        } catch (failure: Exception) {
            for ((target, entry, _, backup) in changes.asReversed().filter { it.committed }) {
                try {
                    val stillOurs = entry?.let(::owns) ?: !Files.exists(target, NOFOLLOW_LINKS)
                    if (stillOurs) {
                        if (backup == null) Files.deleteIfExists(target) else moveFile(backup, target)
                    }
                } catch (rollbackFailure: Exception) {
                    failure.addSuppressed(rollbackFailure)
                }
            }
            throw failure
        } finally {
            for ((_, _, staged, backup) in changes) {
                staged?.let { runCatching { Files.deleteIfExists(it) } }
                backup?.let { runCatching { Files.deleteIfExists(it) } }
            }
        }
    }

    private fun backup(change: Change) {
        if (!Files.exists(change.target, NOFOLLOW_LINKS)) return
        if (!Files.isRegularFile(change.target, NOFOLLOW_LINKS)) {
            throw IOException("Not a regular splash cache file: ${change.target}")
        }
        val backup = Files.createTempFile(directory, ".custom-splash-backup-", ".tmp")
        change.backup = backup
        Files.copy(change.target, backup, REPLACE_EXISTING)
    }

    private fun owns(entry: SplashCacheEntry): Boolean {
        if (!isCacheFileName(entry.fileName)) return false
        val path = directory.resolve(entry.fileName)
        if (!Files.isRegularFile(path, NOFOLLOW_LINKS)) return false
        val digest = MessageDigest.getInstance("SHA-256")
        Files.newInputStream(path).use { stream ->
            val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
            var count = stream.read(buffer)
            while (count != -1) {
                digest.update(buffer, 0, count)
                count = stream.read(buffer)
            }
        }
        return HexFormat.of().formatHex(digest.digest()) == entry.sha256
    }

    private fun encode(image: BufferedImage): ByteArray {
        val pixels = image.getRGB(0, 0, image.width, image.height, null, 0, image.width)
        val buffer =
            ByteBuffer.allocate(Math.addExact(8, Math.multiplyExact(pixels.size, 4))).order(ByteOrder.LITTLE_ENDIAN)
        buffer.putInt(image.width)
        buffer.putInt(image.height)
        buffer.asIntBuffer().put(pixels)
        return buffer.array()
    }

    private fun digest(bytes: ByteArray) = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun isCacheFileName(name: String) = name.matches(Regex("[0-9a-z]+-[0-9a-z]+-[0-9a-z]+\\.ij"))
}

private fun moveCacheFile(source: Path, target: Path) {
    try {
        Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
    } catch (_: AtomicMoveNotSupportedException) {
        Files.move(source, target, REPLACE_EXISTING)
    }
}