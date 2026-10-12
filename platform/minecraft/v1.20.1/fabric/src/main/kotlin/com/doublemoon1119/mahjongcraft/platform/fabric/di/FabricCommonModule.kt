package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.network.dto.di.NetworkDtoModule
import com.doublemoon1119.mahjongcraft.flow.persistence.format.di.PersistenceFormatModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.environment.FabricMinecraftEnvironment
import com.doublemoon1119.mahjongcraft.platform.minecraft.di.MinecraftCommonModule
import com.doublemoon1119.mahjongcraft.platform.minecraft.environment.MinecraftEnvironment
import org.koin.core.annotation.ComponentScan
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/**
 * Fabric client 與 server graph 共用的 Fabric runtime 定義。
 *
 * DTO registry、codec 與序列化由其所屬模組提供；此 module 組合共用模組，並只掃描 metadata 套件。
 * 不掃描整個 Fabric 套件，以免混入 client 或 server 專用服務。
 */
@Module(includes = [NetworkDtoModule::class, PersistenceFormatModule::class, MinecraftCommonModule::class])
@ComponentScan("com.doublemoon1119.mahjongcraft.platform.fabric.metadata")
class FabricCommonModule {
    /**
     * 建立 client 與 server 共用的 Fabric 執行環境判定器。
     *
     * 實作目前位於 server 掃描範圍，保留此共用組裝入口，避免兩個 module 重複掃描同一個定義；
     * 不為統一註冊寫法而搬移既有公開型別。
     *
     * @return 委託 Fabric Loader 判斷開發環境的共用服務。
     */
    @Single
    fun provideMinecraftEnvironment(): MinecraftEnvironment = FabricMinecraftEnvironment()
}
