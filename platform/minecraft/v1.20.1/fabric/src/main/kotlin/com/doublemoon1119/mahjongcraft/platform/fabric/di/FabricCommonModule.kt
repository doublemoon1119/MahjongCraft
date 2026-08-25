package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.network.dto.di.NetworkDtoModule
import com.doublemoon1119.mahjongcraft.flow.persistence.dto.di.PersistenceDtoModule
import com.doublemoon1119.mahjongcraft.platform.fabric.metadata.FabricRuntimeMetadata
import com.doublemoon1119.mahjongcraft.platform.fabric.server.environment.FabricMinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.MinecraftCommonModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Fabric client 與 server graph 共用的 Fabric runtime 定義。
 *
 * DTO registry、codec 與序列化由其所屬模組提供；此 module 只負責組合共用模組與提供 Fabric metadata。
 */
@Module(includes = [NetworkDtoModule::class, PersistenceDtoModule::class, MinecraftCommonModule::class])
class FabricCommonModule {
    /** 建立供兩種 Fabric runtime 記錄版本資訊的 metadata reader。 */
    @Single
    fun provideFabricRuntimeMetadata(): FabricRuntimeMetadata = FabricRuntimeMetadata()

    /** 建立 client 與 server 共用的 Fabric 執行環境判定器。 */
    @Single
    fun provideMinecraftEnvironment(): MinecraftEnvironment = FabricMinecraftEnvironment()
}
