package com.doublemoon1119.mahjongcraft.flow.network.dto.di

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.buildMahjongDtoSerializersModule
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** `:mahjong-flow-network-dto` 擁有的 registry 與序列化 Koin 定義。 */
@Module
class NetworkDtoModule {
    /** 建立供 extension 註冊與 DTO 轉換共用的 runtime registry。 */
    @Single
    fun provideNetworkDtoRegistries(): NetworkDtoRegistries = DefaultNetworkDtoRegistries()

    /**
     * 建立 network 與 snapshot 共用的 [Json]。
     *
     * [registries] 必須在第一次解析 [Json] 前完成 extension bootstrap；Koin single 採延遲建立，因此
     * bootstrap 與序列化會使用相同的 registry 實例。
     */
    @Single
    fun provideJson(registries: NetworkDtoRegistries): Json = Json {
        serializersModule = buildMahjongDtoSerializersModule(registries)
    }
}
