package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 判斷哪些玩家依規則可以搶一次暗槓或加槓。 */
internal object ChankanEligibility {
    /**
     * 回傳可以榮和 [robbedTile] 的玩家，尚未套用一炮多響設定。
     *
     * 這張牌尚未套用進副露，每位其他玩家各問一次合法動作即可；反應分支也會算出吃、碰、明槓資格，
     * 搶槓情境只看榮和。
     *
     * @param tableState 宣告槓前的權威桌況。
     * @param declarerId 宣告槓的玩家。
     * @param kanAction 宣告的暗槓或加槓。
     * @param robbedTile 可被搶的牌。
     * @param module 該對局採用的規則模組。
     * @return 可以榮和的玩家 ID。
     */
    fun ronEligiblePlayerIds(
        tableState: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        robbedTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): Set<Uuid> {
        val validator = module.createLegalActionValidator()
        return tableState.players
            .filter { it.id != declarerId && tableState.isPlayerActive(it.id) }
            .filter { candidate ->
                validator.getLegalActions(
                    tableState = tableState,
                    player = candidate,
                    sourceAction = kanAction,
                    sourceDirection = tableState.relativeDirectionOf(candidate.id, declarerId),
                    incomingTile = robbedTile,
                ).any { it is GameAction.Ron }
            }
            .mapTo(mutableSetOf()) { it.id }
    }
}
