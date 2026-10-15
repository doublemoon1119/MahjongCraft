package com.doublemoon1119.mahjongcraft.flow.network.dto.di

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.buildMahjongDtoSerializersModule
import kotlinx.serialization.json.Json
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * `:mahjong-flow-network-dto` 模組所提供的註冊表與序列化相依項定義。
 *
 * 網路訊息與狀態快照共用的 [Json] 與擴充功能初始化流程使用相同的 [NetworkDtoRegistries]；動態多型序列化器會在每次
 * 編解碼時查詢註冊表，因此即使其他相依項先取得 [Json]，稍後完成的 DTO 註冊仍會生效。
 */
val networkDtoModule = module {
    single {
        Json {
            serializersModule = buildMahjongDtoSerializersModule(get())
        }
    }

    single<DefaultNetworkDtoRegistries>() bind NetworkDtoRegistries::class
}
