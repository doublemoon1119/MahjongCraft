package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.server.di.FlowServerModule
import org.koin.core.annotation.KoinApplication

/**
 * Koin Annotations 的應用程式進入點標記類別，本身不會被實例化——
 * [org.koin.core.context.startKoin] 的 reified `startKoin<MahjongCraftApp>()` 變體在編譯期讀這個
 * 註解，組出 [FlowServerModule]（連同它 `includes` 進來的
 * `com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule`）與 [FabricPlatformModule]
 * 兩個模組合併後的 Koin graph。比照 Koin Annotations 官方文件建議的啟動方式：
 * https://insert-koin.io/docs/reference/koin-annotations/start/
 */
@KoinApplication(modules = [FlowServerModule::class, FabricPlatformModule::class])
class MahjongCraftApp
