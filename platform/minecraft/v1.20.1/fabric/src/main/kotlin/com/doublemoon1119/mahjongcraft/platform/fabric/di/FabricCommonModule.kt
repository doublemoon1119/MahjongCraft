package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.extension.FabricExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.server.environment.FabricMinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.minecraftCommonModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.networkDtoModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.persistenceFormatModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * Fabric client 與 server graph 共用的 Fabric runtime 定義。
 *
 * DTO registry、codec 與序列化由其所屬模組提供；此 module 組合共用模組，並提供兩個 graph 都使用同一個實例的 Fabric 服務：
 * 委託 Fabric Loader 判斷開發環境的執行環境判定器，以及額外隔離 JVM 類別連結錯誤的擴充失敗判斷。
 */
val fabricCommonModule = module {
    includes(networkDtoModule, persistenceFormatModule, minecraftCommonModule)

    single<MinecraftEnvironment> { FabricMinecraftEnvironment() }
    single<ExtensionFailurePolicy> { FabricExtensionFailurePolicy() }

    single<FabricRuntimeMetadata>()
}
