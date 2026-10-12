package com.doublemoon1119.mahjongcraft.flow.network.dto.di

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.buildMahjongDtoSerializersModule
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Module
import org.koin.core.annotation.Single

/** `:mahjong-flow-network-dto` 模組所提供的註冊表與序列化相依項定義。 */
@Module
class NetworkDtoModule {
    /** 建立供擴充功能註冊與 DTO 轉換共用的執行期間註冊表。 */
    @Single
    fun provideNetworkDtoRegistries(): NetworkDtoRegistries = DefaultNetworkDtoRegistries()

    /**
     * 建立供網路訊息與狀態快照共用的 [Json]。
     *
     * [Json] 與擴充功能初始化流程使用相同的 [registries]；動態多型序列化器會在每次
     * 編解碼時查詢註冊表，因此即使其他相依項先取得 [Json]，稍後完成的 DTO 註冊仍會生效。
     *
     * @param registries 網路訊息與擴充功能共用的 DTO 執行期間註冊表。
     */
    @Single
    fun provideJson(registries: NetworkDtoRegistries): Json = Json {
        serializersModule = buildMahjongDtoSerializersModule(registries)
    }
}
