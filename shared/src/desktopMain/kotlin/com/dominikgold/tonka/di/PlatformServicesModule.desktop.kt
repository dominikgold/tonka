package com.dominikgold.tonka.di

import com.dominikgold.tonka.platform.DesktopImageProcessor
import com.dominikgold.tonka.platform.DesktopSecretStore
import com.dominikgold.tonka.platform.ImageProcessor
import com.dominikgold.tonka.platform.PlatformContext
import com.dominikgold.tonka.platform.SecretStore
import com.dominikgold.tonka.platform.appDataDirectory
import org.koin.core.module.Module
import org.koin.dsl.module

actual fun platformServicesModule(context: PlatformContext): Module = module {
    single<SecretStore> { DesktopSecretStore(appDataDirectory(context)) }
    single<ImageProcessor> { DesktopImageProcessor() }
}
