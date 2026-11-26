package com.doublemoon1119.mahjongcraft.platform.minecraft.di

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import org.koin.dsl.bind
import org.koin.dsl.module
import org.koin.plugin.module.dsl.single

/**
 * 平台為 `:mahjong-flow-network-dto` 組裝的註冊表與網路格式定義。
 *
 * [NetworkJson] 與擴充功能初始化流程使用相同的 [NetworkDtoRegistries]。
 */
val networkDtoModule = module {
    single<NetworkJson>()

    single<DefaultNetworkDtoRegistries>() bind NetworkDtoRegistries::class
}
