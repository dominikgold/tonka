package com.dominikgold.tonka.di

import com.dominikgold.tonka.platform.AndroidImageProcessor
import com.dominikgold.tonka.platform.AndroidSecretStore
import com.dominikgold.tonka.platform.ImageProcessor
import com.dominikgold.tonka.platform.PlatformContext
import com.dominikgold.tonka.platform.SecretStore
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformServicesModule(context: PlatformContext): Module = module {
    single<SecretStore> { AndroidSecretStore(context.context) }
    single<ImageProcessor> { AndroidImageProcessor() }
}
