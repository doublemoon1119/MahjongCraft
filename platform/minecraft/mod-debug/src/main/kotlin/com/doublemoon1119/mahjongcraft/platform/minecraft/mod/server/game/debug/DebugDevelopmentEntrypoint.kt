package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.debug.autotest.AutotestSelection
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.MahjongCraftDevelopmentEntrypoint
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.TableEndListeners
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development.WinCelebrationRequestAdjusters
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader.ServerEvents
import com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.autotest.AutotestServerRunner
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.presentation.DebugWinShowcaseOverride
import org.koin.core.Koin
import org.koin.core.module.Module

/**
 * debug 模組接上本體的進入點：載入 debug 服務、接上和牌展示調整與牌桌結束通知，並登記 `/mahjongcraft debug` 指令；開發執行帶自動
 * 測試設定時掛上專用伺服器的自動測試。
 */
class DebugDevelopmentEntrypoint : MahjongCraftDevelopmentEntrypoint {
    override fun koinModules(): List<Module> = listOf(debugServerModule)

    override fun initialize(koin: Koin, events: ServerEvents) {
        val showcaseOverride = koin.get<DebugWinShowcaseOverride>()
        val continuationState = koin.get<DebugWinRoundContinuationState>()
        koin.get<WinCelebrationRequestAdjusters>().register(showcaseOverride::applyTo)
        koin.get<TableEndListeners>().register { tableId ->
            continuationState.clear(tableId)
            showcaseOverride.clear(tableId)
        }
        koin.get<DebugCommand>().register(events)
        // 伺服器自動測試只在 autotestServer 任務指定了階段時執行，其他伺服器即使帶著自動測試開關也照常啟動。
        if (AutotestSelection.current != null && AutotestServerRunner.requestedPhase() != null) AutotestServerRunner(koin).register(events)
    }
}
