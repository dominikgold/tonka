package com.dominikgold.tonka.platform

import com.github.javakeyring.Keyring
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.imageio.IIOImage
import javax.imageio.ImageIO
import javax.imageio.ImageWriteParam
import javax.imageio.stream.MemoryCacheImageOutputStream

// Uses the OS keyring and falls back to an AES-encrypted file when no keyring is available (e.g. headless Linux)
class DesktopSecretStore(private val dataDirectory: Path) : SecretStore {

    private val keyring: Keyring? = runCatching { Keyring.create() }.getOrNull()

    override suspend fun get(key: String): String? = withContext(Dispatchers.IO) {
        keyring?.let { keyring ->
            runCatching { keyring.getPassword(SERVICE, key) }.getOrNull()?.let { return@withContext it }
        }
        readFallback(key)
    }

    override suspend fun set(key: String, value: String?) = withContext(Dispatchers.IO) {
        val storedInKeyring = keyring?.let { keyring ->
            runCatching {
                if (value == null) {
                    runCatching { keyring.deletePassword(SERVICE, key) }
                } else {
                    keyring.setPassword(SERVICE, key, value)
                }
            }.isSuccess
        } ?: false
        if (storedInKeyring && value != null) {
            writeFallback(key, null)
        } else {
            writeFallback(key, value)
        }
    }

    private fun readFallback(key: String): String? {
        val file = fallbackFile(key)
        if (!FileSystem.SYSTEM.exists(file)) return null
        return runCatching {
            val bytes = Base64.getDecoder().decode(FileSystem.SYSTEM.read(file) { readUtf8() })
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.DECRYPT_MODE, fallbackKey(), GCMParameterSpec(GCM_TAG_BITS, bytes, 0, GCM_IV_LENGTH))
            cipher.doFinal(bytes, GCM_IV_LENGTH, bytes.size - GCM_IV_LENGTH).decodeToString()
        }.getOrNull()
    }

    private fun writeFallback(key: String, value: String?) {
        val file = fallbackFile(key)
        if (value == null) {
            FileSystem.SYSTEM.delete(file, mustExist = false)
            return
        }
        val iv = ByteArray(GCM_IV_LENGTH).also { SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, fallbackKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        val encrypted = iv + cipher.doFinal(value.encodeToByteArray())
        FileSystem.SYSTEM.write(file) { writeUtf8(Base64.getEncoder().encodeToString(encrypted)) }
        restrictToOwner(file)
    }

    private fun fallbackKey(): SecretKeySpec {
        val keyFile = dataDirectory / "secrets" / "store.key"
        if (!FileSystem.SYSTEM.exists(keyFile)) {
            FileSystem.SYSTEM.createDirectories(keyFile.parent!!)
            val key = ByteArray(32).also { SecureRandom().nextBytes(it) }
            FileSystem.SYSTEM.write(keyFile) { write(key) }
            restrictToOwner(keyFile)
        }
        return SecretKeySpec(FileSystem.SYSTEM.read(keyFile) { readByteArray() }, "AES")
    }

    private fun fallbackFile(key: String): Path {
        val directory = dataDirectory / "secrets"
        FileSystem.SYSTEM.createDirectories(directory)
        return directory / "$key.secret"
    }

    private fun restrictToOwner(path: Path) {
        runCatching { Files.setPosixFilePermissions(path.toNioPath(), PosixFilePermissions.fromString("rw-------")) }
    }

    private companion object {
        const val SERVICE = "Tonka"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val GCM_IV_LENGTH = 12
        const val GCM_TAG_BITS = 128
    }
}

class DesktopImageProcessor : ImageProcessor {

    override suspend fun toJpeg(
        bytes: ByteArray,
        maxLongEdge: Int,
        quality: Int,
        cropAspectRatio: Float?,
    ): ProcessedImage? = withContext(Dispatchers.IO) {
        val source = runCatching { ImageIO.read(ByteArrayInputStream(bytes)) }.getOrNull() ?: return@withContext null
        val crop = centerCrop(source.width, source.height, cropAspectRatio)
        val cropped = source.getSubimage(crop.left, crop.top, crop.width, crop.height)
        val (width, height) = scaledSize(cropped.width, cropped.height, maxLongEdge)
        val scaled = BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
        scaled.createGraphics().apply {
            setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR)
            setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY)
            drawImage(cropped, 0, 0, width, height, null)
            dispose()
        }
        val writer = ImageIO.getImageWritersByFormatName("jpeg").next()
        val output = ByteArrayOutputStream()
        MemoryCacheImageOutputStream(output).use { stream ->
            writer.output = stream
            val parameters = writer.defaultWriteParam.apply {
                compressionMode = ImageWriteParam.MODE_EXPLICIT
                compressionQuality = quality / 100f
            }
            writer.write(null, IIOImage(scaled, null, null), parameters)
            writer.dispose()
        }
        ProcessedImage(jpegBytes = output.toByteArray(), width = width, height = height)
    }
}
