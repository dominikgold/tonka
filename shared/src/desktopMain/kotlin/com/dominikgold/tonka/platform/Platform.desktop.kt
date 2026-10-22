package com.dominikgold.tonka.platform

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.dominikgold.tonka.data.db.TonkaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.util.Properties

actual class PlatformContext

actual fun createSqlDriver(context: PlatformContext): SqlDriver {
    val databasePath = appDataDirectory(context) / DATABASE_FILE_NAME
    val driver = JdbcSqliteDriver(
        url = "jdbc:sqlite:$databasePath",
        properties = Properties().apply { put("foreign_keys", "true") },
    )
    val currentVersion = driver.executeQuery(
        identifier = null,
        sql = "PRAGMA user_version",
        mapper = { cursor -> QueryResult.Value(if (cursor.next().value) cursor.getLong(0) else 0L) },
        parameters = 0,
    ).value ?: 0L
    val schemaVersion = TonkaDatabase.Schema.version
    if (currentVersion == 0L) {
        TonkaDatabase.Schema.create(driver)
        driver.execute(null, "PRAGMA user_version = $schemaVersion", 0)
    } else if (currentVersion < schemaVersion) {
        TonkaDatabase.Schema.migrate(driver, currentVersion, schemaVersion)
        driver.execute(null, "PRAGMA user_version = $schemaVersion", 0)
    }
    return driver
}

actual fun appDataDirectory(context: PlatformContext): Path {
    val home = System.getProperty("user.home")
    val os = System.getProperty("os.name").lowercase()
    val directory = when {
        os.contains("mac") -> "$home/Library/Application Support/Tonka"
        os.contains("win") -> (System.getenv("APPDATA") ?: "$home/AppData/Roaming") + "/Tonka"
        else -> (System.getenv("XDG_DATA_HOME") ?: "$home/.local/share") + "/tonka"
    }.toPath()
    FileSystem.SYSTEM.createDirectories(directory)
    return directory
}

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    installDefaults()
}
