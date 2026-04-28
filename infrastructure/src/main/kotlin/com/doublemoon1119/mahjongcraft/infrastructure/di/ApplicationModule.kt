package com.doublemoon1119.mahjongcraft.infrastructure.di

import org.koin.dsl.module

/**
 * 應用程式層 (Application Layer) 的依賴注入配置。
 *
 * 此模組定義於 `infrastructure` 層，旨在隔離 `infrastructure` 層與 DI 框架的依賴關係。
 * 負責宣告所有業務邏輯執行單元（Use Cases）的實例化規則與依賴注入邏輯。
 */
val applicationModule = module {
    // 未來在此處定義 Use Case 的注入，例如：
    // factoryOf(::StartGameUseCase)
}