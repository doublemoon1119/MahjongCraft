package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/** 判斷哪些玩家依規則可以搶一次暗槓、加槓或移出手牌的動作。 */
internal object RobbingEligibility {
    /**
     * 回傳可以榮和 [robbedTile] 的玩家，尚未套用一炮多響設定。
     *
     * 這張牌尚未套用進副露或移出區，每位其他玩家各問一次合法動作即可；反應分支也會算出吃、碰、明槓資格，
     * 搶和情境只看榮和。
     *
     * @param tableState 宣告動作前的權威桌況。
     * @param declarerId 宣告動作的玩家。
     * @param declaredAction 宣告的暗槓、加槓或移出手牌的擴充動作。
     * @param robbedTile 可被搶的牌。
     * @param module 該對局採用的規則模組。
     * @return 可以榮和的玩家 ID。
     */
    fun ronEligiblePlayerIds(
        tableState: TableState,
        declarerId: Uuid,
        declaredAction: GameAction,
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
                    sourceAction = declaredAction,
                    sourceDirection = tableState.relativeDirectionOf(candidate.id, declarerId),
                    incomingTile = robbedTile,
                ).any { it is GameAction.Ron }
            }
            .mapTo(mutableSetOf()) { it.id }
    }
}
