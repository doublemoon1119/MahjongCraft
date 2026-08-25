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
     * [Json] 與 extension bootstrap 使用相同的 [registries]；動態 polymorphic provider 會在每次
     * 編解碼時查詢 registry，因此即使其他 Koin 依賴提前解析 [Json]，稍後完成的 DTO 註冊仍然可見。
     */
    @Single
    fun provideJson(registries: NetworkDtoRegistries): Json = Json {
        serializersModule = buildMahjongDtoSerializersModule(registries)
    }
}
