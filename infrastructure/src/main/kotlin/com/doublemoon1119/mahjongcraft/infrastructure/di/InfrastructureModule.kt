package com.doublemoon1119.mahjongcraft.infrastructure.di

import com.doublemoon1119.mahjongcraft.domain.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.infrastructure.module.MahjongModuleRegistryImpl
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module


/**
 * 基礎設施層 (Infrastructure Layer) 的依賴注入配置模組。
 *
 * 此模組負責將 `:infrastructure` 層的具體實作類別與 `domain` 或 `application` 層定義的抽象介面進行綁定。
 * 透過 Koin 框架提供的 DSL 進行實例化管理，確保系統組件的解耦與單例生命週期控制。
 */
val infrastructureModule = module {
    /**
     * 註冊麻將模組註冊表的單例實作。
     * 將 [MahjongModuleRegistryImpl] 綁定至 [MahjongModuleRegistry] 介面。
     */
    singleOf(::MahjongModuleRegistryImpl) { bind<MahjongModuleRegistry>() }
}