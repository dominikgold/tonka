package com.dominikgold.tonka.platform

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.dominikgold.tonka.data.db.TonkaDatabase
import io.ktor.client.HttpClient
import io.ktor.client.engine.okhttp.OkHttp
import okio.Path
import okio.Path.Companion.toOkioPath

actual class PlatformContext(val context: Context)

actual fun createSqlDriver(context: PlatformContext): SqlDriver = AndroidSqliteDriver(
    schema = TonkaDatabase.Schema,
    context = context.context,
    name = DATABASE_FILE_NAME,
    callback = object : AndroidSqliteDriver.Callback(TonkaDatabase.Schema) {
        override fun onConfigure(db: SupportSQLiteDatabase) {
            db.setForeignKeyConstraintsEnabled(true)
        }
    },
)

actual fun appDataDirectory(context: PlatformContext): Path = context.context.filesDir.toOkioPath()

actual fun createHttpClient(): HttpClient = HttpClient(OkHttp) {
    installDefaults()
}
