package com.dominikgold.tonka

import com.dominikgold.tonka.data.IngredientRepository
import com.dominikgold.tonka.data.SeedData
import com.dominikgold.tonka.di.AppScope
import com.dominikgold.tonka.di.sharedModules
import com.dominikgold.tonka.di.uiModule
import com.dominikgold.tonka.platform.PlatformContext
import com.dominikgold.tonka.resources.Res
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.ExperimentalResourceApi
import org.koin.core.context.startKoin
import org.koin.mp.KoinPlatform

private const val SEED_PATH = "files/seed/ingredients.json"

// Called once per process from each platform's entry point
fun startTonka(context: PlatformContext) {
    if (KoinPlatform.getKoinOrNull() != null) return
    val koin = startKoin { modules(sharedModules(context) + uiModule) }.koin
    koin.get<CoroutineScope>(AppScope).launch { applySeed(koin.get()) }
}

// The seed is applied on first launch and again whenever its version increases; user edits are never overwritten
@OptIn(ExperimentalResourceApi::class)
private suspend fun applySeed(ingredientRepository: IngredientRepository) {
    runCatching {
        val seed = SeedData.parse(Res.readBytes(SEED_PATH).decodeToString())
        ingredientRepository.applySeed(seed)
    }.onFailure { println("Applying the ingredient seed failed: ${it.message}") }
}
