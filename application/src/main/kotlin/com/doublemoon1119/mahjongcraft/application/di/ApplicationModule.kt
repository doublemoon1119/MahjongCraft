package com.doublemoon1119.mahjongcraft.application.di

import com.doublemoon1119.mahjongcraft.application.ports.concurrency.CoroutineDispatchers
import com.doublemoon1119.mahjongcraft.application.ports.concurrency.DefaultCoroutineDispatchers
import com.doublemoon1119.mahjongcraft.application.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.application.usecase.StartGameUseCase
import com.doublemoon1119.mahjongcraft.application.usecase.factory.MahjongModuleRegistry
import org.koin.core.module.dsl.bind
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

/**
 * 應用層 (Application Layer) 的 Koin 依賴注入模組。
 *
 * 此模組集中管理 `application` 層內所有可注入元件的生命週期與依賴關係。
 * 主要負責綁定以下類型的元件：
 * - **業務邏輯使用案例 (Use Cases)**：處理核心業務流程的類別。
 * - **應用層服務 (Application Services)**：提供如協程調度、模組註冊等跨使用案例的通用功能。
 * - **介面與實作的映射 (Port/Adapter Binding)**：將抽象介面（Port）綁定到其預設的具體實作（Adapter）。
 */
val applicationModule = module {

    // 併發處理相關
    // 提供 DefaultCoroutineDispatchers 作為 CoroutineDispatchers 的預設實作
    // 平台層 (Platform Layer) 可透過 loadKoinModules 覆蓋此定義以提供平台特定的 Dispatcher
    singleOf(::DefaultCoroutineDispatchers) { bind<CoroutineDispatchers>() }

    // 領域服務
    singleOf(::MahjongModuleRegistry)

    // 使用案例 (Use Cases)
    // 每次請求都建立新的實例 (Factory scope)，確保無狀態或狀態獨立
    factoryOf(::StartGameUseCase)
    factoryOf(::DrawTileUseCase)
}
