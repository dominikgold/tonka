package com.dominikgold.tonka.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import com.dominikgold.tonka.data.AppClock
import com.dominikgold.tonka.data.ImageStore
import com.dominikgold.tonka.data.db.TonkaDatabase
import com.dominikgold.tonka.domain.Tuning
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okio.Buffer
import okio.FileSystem
import okio.Path
import kotlin.time.Instant

class BackupException(message: String, cause: Throwable? = null) : Exception(message, cause)

@Serializable
data class BackupManifest(
    val formatVersion: Int,
    val appVersion: String,
    val exportedAt: String,
    val counts: Map<String, Int>,
)

data class BackupPreview(
    val manifest: BackupManifest,
    val recipeCount: Int,
    val ingredientCount: Int,
    val mealPlanEntryCount: Int,
    val imageCount: Int,
)

class BackupService(
    private val database: TonkaDatabase,
    private val driver: SqlDriver,
    private val imageStore: ImageStore,
    private val clock: AppClock,
    private val fileSystem: FileSystem = FileSystem.SYSTEM,
) {

    private val backupsDirectory: Path get() = imageStore.dataDirectory / "backups"

    fun defaultFileName(): String = "recipes-${clock.today()}.$FILE_EXTENSION"

    suspend fun export(onProgress: (Float) -> Unit = {}): ByteArray = withContext(Dispatchers.IO) {
        val tables = database.transactionWithResult { readTables() }
        val images = imageFiles()
        val manifest = BackupManifest(
            formatVersion = FORMAT_VERSION,
            appVersion = APP_VERSION,
            exportedAt = Instant.fromEpochMilliseconds(clock.nowMillis()).toString(),
            counts = tables.mapValues { it.value.size } + (IMAGES_COUNT_KEY to images.size),
        )
        val buffer = Buffer()
        val writer = StoreZipWriter(buffer)
        writer.add(MANIFEST_FILE, json.encodeToString(BackupManifest.serializer(), manifest).encodeToByteArray())
        writer.add(DATA_FILE, encodeData(tables).encodeToByteArray())
        images.forEachIndexed { index, path ->
            writer.add("${ImageStore.IMAGES_FOLDER}/${path.name}", fileSystem.read(path) { readByteArray() })
            onProgress((index + 1).toFloat() / images.size)
        }
        writer.finish()
        buffer.readByteArray()
    }

    suspend fun inspect(bytes: ByteArray): BackupPreview = withContext(Dispatchers.Default) {
        val (manifest, tables, images) = unpack(bytes)
        BackupPreview(
            manifest = manifest,
            recipeCount = tables["recipe"]?.size ?: 0,
            ingredientCount = tables["ingredient"]?.size ?: 0,
            mealPlanEntryCount = tables["meal_plan_entry"]?.size ?: 0,
            imageCount = images.size,
        )
    }

    // Replaces all data with the backup's; on any failure the current data stays untouched
    suspend fun restore(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val (_, tables, images) = unpack(bytes)
        writeSafetyBackup()

        val stagingDirectory = imageStore.dataDirectory / "images_import"
        val previousDirectory = imageStore.dataDirectory / "images_previous"
        fileSystem.deleteRecursively(stagingDirectory, mustExist = false)
        try {
            fileSystem.createDirectories(stagingDirectory)
            images.forEach { (name, data) ->
                val fileName = name.substringAfterLast('/')
                if (fileName.isNotEmpty() && !fileName.startsWith(".")) {
                    fileSystem.write(stagingDirectory / fileName) { write(data) }
                }
            }
            database.transaction {
                BackupSchema.asReversed().forEach { table -> driver.execute(null, "DELETE FROM ${table.name}", 0) }
                database.recipeQueries.deleteAllDrafts()
                BackupSchema.forEach { table -> insertRows(table, tables[table.name].orEmpty()) }
            }
        } catch (e: Exception) {
            fileSystem.deleteRecursively(stagingDirectory, mustExist = false)
            throw BackupException("The backup could not be imported. Your existing data was not changed.", e)
        }
        fileSystem.deleteRecursively(previousDirectory, mustExist = false)
        if (fileSystem.exists(imageStore.imagesDirectory)) fileSystem.atomicMove(imageStore.imagesDirectory, previousDirectory)
        fileSystem.atomicMove(stagingDirectory, imageStore.imagesDirectory)
        fileSystem.deleteRecursively(previousDirectory, mustExist = false)
        driver.notifyListeners(*(BackupSchema.map { it.name } + "recipe_draft").toTypedArray())
    }

    private data class Unpacked(
        val manifest: BackupManifest,
        val tables: Map<String, List<JsonObject>>,
        val images: Map<String, ByteArray>,
    )

    private fun unpack(bytes: ByteArray): Unpacked {
        val entries = try {
            StoreZipReader.read(bytes)
        } catch (e: ZipFormatException) {
            throw BackupException(e.message ?: "Not a backup file", e)
        }
        val manifest = entries[MANIFEST_FILE]?.let { runCatching { json.decodeFromString(BackupManifest.serializer(), it.decodeToString()) }.getOrNull() }
            ?: throw BackupException("This file is not a recipe backup (its manifest is missing).")
        if (manifest.formatVersion > FORMAT_VERSION) {
            throw BackupException("This backup was made by a newer version of the app. Update the app to import it.")
        }
        val data = entries[DATA_FILE]?.decodeToString() ?: throw BackupException("The backup has no data.")
        val tables = runCatching { migrate(manifest.formatVersion, json.parseToJsonElement(data).jsonObject) }
            .getOrElse { throw BackupException("The backup's data is damaged.", it) }
        val images = entries.filterKeys { it.startsWith("${ImageStore.IMAGES_FOLDER}/") }
        return Unpacked(manifest, tables, images)
    }

    // Older formats pass through versioned migrators here before import
    private fun migrate(formatVersion: Int, data: JsonObject): Map<String, List<JsonObject>> {
        val tables = data["tables"]?.jsonObject ?: throw BackupException("The backup's data is damaged.")
        return when (formatVersion) {
            1 -> tables.mapValues { (_, rows) -> (rows as JsonArray).map { it.jsonObject } }
            else -> throw BackupException("Unsupported backup format $formatVersion.")
        }
    }

    private fun readTables(): Map<String, List<JsonObject>> = BackupSchema.associate { table ->
        val sql = "SELECT ${table.columns.joinToString { it.name }} FROM ${table.name} ORDER BY ${table.orderBy.joinToString()}"
        table.name to driver.executeQuery(
            identifier = null,
            sql = sql,
            mapper = { cursor ->
                val rows = mutableListOf<JsonObject>()
                while (cursor.next().value) {
                    rows += JsonObject(
                        table.columns.mapIndexed { index, column ->
                            column.name to when (column.type) {
                                ColumnType.Text -> cursor.getString(index)?.let(::JsonPrimitive) ?: JsonNull
                                ColumnType.Integer -> cursor.getLong(index)?.let(::JsonPrimitive) ?: JsonNull
                            }
                        }.toMap(),
                    )
                }
                QueryResult.Value(rows.toList())
            },
            parameters = 0,
        ).value
    }

    private fun insertRows(table: BackupTable, rows: List<JsonObject>) {
        val sql = "INSERT INTO ${table.name} (${table.columns.joinToString { it.name }}) " +
            "VALUES (${table.columns.joinToString { "?" }})"
        rows.forEach { row ->
            driver.execute(null, sql, table.columns.size) {
                table.columns.forEachIndexed { index, column ->
                    val value = row[column.name]
                    when (column.type) {
                        ColumnType.Text -> bindString(index, value.asText())
                        ColumnType.Integer -> bindLong(index, value.asLong())
                    }
                }
            }
        }
    }

    private fun encodeData(tables: Map<String, List<JsonObject>>): String =
        json.encodeToString(JsonElement.serializer(), JsonObject(mapOf("tables" to JsonObject(tables.mapValues { JsonArray(it.value) }))))

    private fun imageFiles(): List<Path> {
        val directory = imageStore.imagesDirectory
        if (!fileSystem.exists(directory)) return emptyList()
        return fileSystem.list(directory).filter { fileSystem.metadata(it).isRegularFile && !it.name.startsWith(".") }.sortedBy { it.name }
    }

    private suspend fun writeSafetyBackup() {
        val bytes = export()
        fileSystem.createDirectories(backupsDirectory)
        val timestamp = Instant.fromEpochMilliseconds(clock.nowMillis())
            .toLocalDateTime(TimeZone.currentSystemDefault())
            .toString()
            .replace(':', '-')
        fileSystem.write(backupsDirectory / "safety-$timestamp.$FILE_EXTENSION") { write(bytes) }
        fileSystem.list(backupsDirectory)
            .filter { it.name.startsWith("safety-") }
            .sortedByDescending { it.name }
            .drop(Tuning.SAFETY_BACKUPS_TO_KEEP)
            .forEach { fileSystem.delete(it, mustExist = false) }
    }

    companion object {
        const val FILE_EXTENSION = "recipebook"
        const val FORMAT_VERSION = 1
        const val APP_VERSION = "1.0.0"
        private const val MANIFEST_FILE = "manifest.json"
        private const val DATA_FILE = "data.json"
        private const val IMAGES_COUNT_KEY = "images"

        private val json = Json {
            ignoreUnknownKeys = true
            prettyPrint = false
        }
    }
}

private fun JsonElement?.asText(): String? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> contentOrNull
    else -> toString()
}

private fun JsonElement?.asLong(): Long? = when (this) {
    null, JsonNull -> null
    is JsonPrimitive -> longOrNull ?: booleanOrNull?.let { if (it) 1L else 0L }
    else -> null
}
