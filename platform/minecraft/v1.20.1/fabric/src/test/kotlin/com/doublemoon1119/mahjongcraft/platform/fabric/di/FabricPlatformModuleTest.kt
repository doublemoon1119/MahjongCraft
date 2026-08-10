package com.doublemoon1119.mahjongcraft.platform.fabric.di

import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.room.service.RoomEventPublisher
import com.doublemoon1119.mahjongcraft.flow.dto.registerBuiltInRuleConfigDtos
import com.doublemoon1119.mahjongcraft.flow.server.di.FlowServerModule
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.GameFlowCoordinator
import org.koin.core.context.stopKoin
import org.koin.plugin.module.dsl.startKoin
import kotlin.test.AfterTest
import kotlin.test.Test

/**
 * Smoke test：確認 [MahjongCraftApp]（也就是 [com.doublemoon1119.mahjongcraft.platform.fabric.MahjongCraftMod]
 * 實際啟動 Koin 時用的那個 `@KoinApplication` 組合——[FlowServerModule]，連同它 `includes` 進來的
 * `com.doublemoon1119.mahjongcraft.flow.common.di.FlowCommonModule`，再加上 [FabricPlatformModule]）
 * 解得出對局流程真正會用到的三個型別，不缺任何綁定。
 */
class FabricPlatformModuleTest {
    @AfterTest
    fun tearDown() {
        stopKoin()
    }

    @Test
    fun `Koin graph resolves GameFlowCoordinator, GameEventPublisher and RoomEventPublisher`() {
        registerBuiltInRuleConfigDtos()
        val koin = startKoin<MahjongCraftApp>().koin

        koin.get<GameFlowCoordinator>()
        koin.get<GameEventPublisher>()
        koin.get<RoomEventPublisher>()
    }
}
