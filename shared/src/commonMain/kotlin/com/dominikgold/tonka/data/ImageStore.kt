package com.dominikgold.tonka.data

import com.dominikgold.tonka.domain.Tuning
import com.dominikgold.tonka.model.newId
import com.dominikgold.tonka.platform.ImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path

// Images live under <data dir>/images; the database only stores paths relative to the data dir
class ImageStore(
    val dataDirectory: Path,
    private val imageProcessor: ImageProcessor,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {

    val imagesDirectory: Path get() = dataDirectory / IMAGES_FOLDER

    fun resolve(relativePath: String): Path = dataDirectory / relativePath

    fun absolutePath(relativePath: String?): String? = relativePath?.let { resolve(it).toString() }

    suspend fun store(
        bytes: ByteArray,
        cropAspectRatio: Float? = null,
    ): String? {
        val processed = imageProcessor.toJpeg(
            bytes = bytes,
            maxLongEdge = Tuning.STORED_IMAGE_MAX_LONG_EDGE,
            quality = Tuning.STORED_IMAGE_JPEG_QUALITY,
            cropAspectRatio = cropAspectRatio,
        ) ?: return null
        return writeJpeg(processed.jpegBytes)
    }

    suspend fun writeJpeg(jpegBytes: ByteArray): String = withContext(Dispatchers.IO) {
        fileSystem.createDirectories(imagesDirectory)
        val relativePath = "$IMAGES_FOLDER/${newId()}.jpg"
        fileSystem.write(resolve(relativePath)) { write(jpegBytes) }
        relativePath
    }

    suspend fun read(relativePath: String): ByteArray? = withContext(Dispatchers.IO) {
        val path = resolve(relativePath)
        if (fileSystem.exists(path)) fileSystem.read(path) { readByteArray() } else null
    }

    suspend fun delete(relativePaths: Collection<String>) = withContext(Dispatchers.IO) {
        relativePaths.forEach { fileSystem.delete(resolve(it), mustExist = false) }
    }

    companion object {
        const val IMAGES_FOLDER = "images"
    }
}
