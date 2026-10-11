package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.AutomatedAdvanceFollowUp
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * 每次自動推進最後補做真人玩家的自動摸牌。
 *
 * @property autoDrawService 執行真人玩家自動摸牌的服務。
 */
@Single(binds = [AutomatedAdvanceFollowUp::class])
class FabricAutomatedAdvanceFollowUp(
    private val autoDrawService: MahjongAutoDrawService,
) : AutomatedAdvanceFollowUp {
    override suspend fun afterAdvance(gameId: Uuid): Boolean = autoDrawService.checkAndAutoDraw(gameId)
}
