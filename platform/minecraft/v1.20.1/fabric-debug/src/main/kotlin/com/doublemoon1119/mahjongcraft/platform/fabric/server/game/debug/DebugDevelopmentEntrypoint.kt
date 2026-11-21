package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.platform.fabric.development.MahjongCraftDevelopmentEntrypoint
import com.doublemoon1119.mahjongcraft.platform.fabric.development.TableEndListeners
import com.doublemoon1119.mahjongcraft.platform.fabric.development.WinCelebrationRequestAdjusters
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinShowcaseOverride
import org.koin.core.Koin
import org.koin.core.module.Module

/** debug 模組接上本體的進入點：載入 debug 服務、接上和牌展示調整與牌桌結束通知，並登記 `/mahjongcraft debug` 指令。 */
class DebugDevelopmentEntrypoint : MahjongCraftDevelopmentEntrypoint {
    override fun koinModules(): List<Module> = listOf(debugServerModule)

    override fun initialize(koin: Koin) {
        val showcaseOverride = koin.get<DebugWinShowcaseOverride>()
        val continuationState = koin.get<DebugWinRoundContinuationState>()
        koin.get<WinCelebrationRequestAdjusters>().register(showcaseOverride::applyTo)
        koin.get<TableEndListeners>().register { tableId ->
            continuationState.clear(tableId)
            showcaseOverride.clear(tableId)
        }
        koin.get<FabricDebugCommand>().register()
    }
}
