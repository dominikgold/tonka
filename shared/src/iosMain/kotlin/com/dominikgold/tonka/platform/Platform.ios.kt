package com.dominikgold.tonka.platform

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import com.dominikgold.tonka.data.db.TonkaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.darwin.Darwin
import okio.Path
import okio.Path.Companion.toPath
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSUserDomainMask

actual class PlatformContext

actual fun createSqlDriver(context: PlatformContext): SqlDriver = NativeSqliteDriver(
    schema = TonkaDatabase.Schema,
    name = DATABASE_FILE_NAME,
    onConfiguration = { configuration ->
        configuration.copy(
            extendedConfig = DatabaseConfiguration.Extended(foreignKeyConstraints = true),
        )
    },
)

actual fun appDataDirectory(context: PlatformContext): Path {
    val directory = NSSearchPathForDirectoriesInDomains(NSApplicationSupportDirectory, NSUserDomainMask, true)
        .first() as String
    NSFileManager.defaultManager.createDirectoryAtPath(directory, withIntermediateDirectories = true, attributes = null, error = null)
    return directory.toPath()
}

actual fun createHttpClient(): HttpClient = HttpClient(Darwin) {
    installDefaults()
}
