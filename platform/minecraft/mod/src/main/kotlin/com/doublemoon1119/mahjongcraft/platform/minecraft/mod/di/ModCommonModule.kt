package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.di

import com.doublemoon1119.mahjongcraft.flow.persistence.format.history.SkippedHistoryEventReporter
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.minecraftCommonModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.networkDtoModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.persistenceFormatModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.metadata.RuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.environment.LoaderMinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.persistence.LoggingSkippedHistoryEventReporter
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Client 與 server graph 共用、與載入器無關的定義。
 *
 * DTO registry、codec 與序列化由其所屬模組提供；此 module 組合共用模組，並提供兩個 graph 都使用同一個實例的服務：
 * 依載入器資訊判斷開發環境的執行環境判定器、啟動訊息使用的執行環境資訊，以及把存檔時略過的歷史事件寫進日誌的回報。
 * 載入器資訊本身由各載入器的模組提供。
 */
val modCommonModule = module {
    includes(networkDtoModule, persistenceFormatModule, minecraftCommonModule)

    single<MinecraftEnvironment> { LoaderMinecraftEnvironment(get()) }

    single<RuntimeMetadata>()

    single<LoggingSkippedHistoryEventReporter>() bind SkippedHistoryEventReporter::class
}
