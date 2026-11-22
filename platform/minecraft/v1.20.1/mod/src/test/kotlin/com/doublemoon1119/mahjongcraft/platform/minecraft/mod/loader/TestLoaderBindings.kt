package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import org.koin.core.module.Module
import org.koin.dsl.module
import java.nio.file.Path

/** 測試用的載入器資訊，不依賴任何載入器。 */
internal object TestLoaderPlatform : LoaderPlatform {
    override val name: String = "test"
    override val isClient: Boolean = true
    override val isDevelopment: Boolean = false
    override val configDirectory: Path = Path.of("config")
    override val modVersion: String = "test"

    override fun isModLoaded(modId: String): Boolean = false
}

/** 與載入器無關的模組需要由載入器補上的定義，測試以不依賴任何載入器的版本提供。 */
internal val testLoaderModule: Module = module {
    single<LoaderPlatform> { TestLoaderPlatform }
    single<ExtensionFailurePolicy> { ExtensionFailurePolicy.Default }
    single<BlockUseInterception> { BlockUseInterception { _, _, _, _ -> true } }
    single<ShadowPassProbe> { ShadowPassProbe { false } }
}
