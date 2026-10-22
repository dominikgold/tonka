package com.dominikgold.tonka.di

import com.dominikgold.tonka.backup.BackupService
import com.dominikgold.tonka.data.AppClock
import com.dominikgold.tonka.data.DraftRepository
import com.dominikgold.tonka.data.ImageStore
import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.data.MealPlanRepository
import com.dominikgold.tonka.data.RecipeRepository
import com.dominikgold.tonka.data.SettingsRepository
import com.dominikgold.tonka.data.SystemAppClock
import com.dominikgold.tonka.data.db.TonkaDatabase
import com.dominikgold.tonka.domain.RecipeIndexProvider
import com.dominikgold.tonka.importing.ClaudeClient
import com.dominikgold.tonka.importing.ImportService
import com.dominikgold.tonka.importing.WebRecipeImporter
import com.dominikgold.tonka.platform.PlatformContext
import com.dominikgold.tonka.platform.appDataDirectory
import com.dominikgold.tonka.platform.createHttpClient
import com.dominikgold.tonka.platform.createSqlDriver
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import org.koin.core.module.Module
import org.koin.core.qualifier.named
import org.koin.dsl.module

val AppScope = named("appScope")

// SecretStore and ImageProcessor implementations per platform
expect fun platformServicesModule(context: PlatformContext): Module

fun dataModule(context: PlatformContext) = module {
    single<CoroutineScope>(AppScope) { CoroutineScope(SupervisorJob() + Dispatchers.Default) }
    single { createSqlDriver(context) }
    single { TonkaDatabase(get()) }
    single<AppClock> { SystemAppClock() }
    single { ImageStore(appDataDirectory(context), get()) }
    single { SettingsRepository(get(), get()) }
    single { IngredientRepository(get(), get(), get(AppScope)) }
    single { RecipeRepository(get(), get(), get(), get(AppScope)) }
    single { MealPlanRepository(get(), get(AppScope)) }
    single { DraftRepository(get(), get()) }
}

val domainModule = module {
    single { RecipeIndexProvider(get(), get(), get(), get(AppScope)) }
}

val importingModule = module {
    single { createHttpClient() }
    single { WebRecipeImporter(get()) }
    single { ClaudeClient(get()) }
    single { ImportService(get(), get(), get(), get(), get(), get(), get(), get(), get()) }
}

val backupModule = module {
    single { BackupService(get(), get(), get(), get()) }
}

fun sharedModules(context: PlatformContext): List<Module> = listOf(
    platformServicesModule(context),
    dataModule(context),
    domainModule,
    importingModule,
    backupModule,
)
