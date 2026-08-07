package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.util.withoutRed
import kotlin.uuid.Uuid

/**
 * 套用暗槓/加槓副露並從死牌區補摸嶺上牌。
 *
 * 從 [DeclareKanUseCase] 抽出的共用邏輯：無人可搶槓時直接套用、以及有人可搶槓但全員選擇放過
 * （[RespondToChankanUseCase]）時「補做」原本被暫緩的套用，兩處需要完全相同的一段邏輯，抽成
 * 共用物件避免重複。依序 `recordAction(kanAction)` → 補摸嶺上牌 → `recordAction(GameAction.Draw)`，
 * 讓 [com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContextCalculator] 既有的
 * 嶺上開花偵測邏輯（依賴 `actionHistory` 最後兩筆是否恰為 `[Kan, Draw]`）能真正被觸發，並呼叫
 * `module.onMeldClaimed` 清除全場一發。
 */
internal object KanDeclarationApplier {
    /**
     * @property tableState 套用副露與嶺上摸牌後的新桌況；牌山恰好摸盡時（[rinshanTile] 為 null）
     *           與呼叫前的 [apply] 參數 `state` 內容相同，未套用任何變化。
     * @property rinshanTile 補摸到的嶺上牌；牌山恰好摸盡時為 null，呼叫端此時應視為
     *           [com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError.WallExhausted]。
     */
    data class Result(val tableState: TableState, val rinshanTile: IdentifiedTile?)

    /**
     * @param state 目前的桌況（尚未套用本次槓牌）。
     * @param declarerId 宣告暗槓/加槓的玩家 Uuid。
     * @param kanAction 本次宣告的具體動作（僅支援 [GameAction.KanType.CLOSED_KAN]／
     *        [GameAction.KanType.ADDED_KAN]，[GameAction.KanType.OPEN_KAN] 走 [RespondToDiscardUseCase]）。
     * @param incomingTile 觸發槓牌的那張牌。
     * @param module 該對局採用的規則模組。
     */
    fun apply(
        state: TableState,
        declarerId: Uuid,
        kanAction: GameAction.Kan,
        incomingTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): Result {
        val declarer = state.players.first { it.id == declarerId }
        val handAfterMeld = when (kanAction.type) {
            GameAction.KanType.CLOSED_KAN -> {
                val kanTiles = kanAction.withTiles.mapNotNull { id ->
                    declarer.hand.standingTiles.find { it.id == id }
                } + incomingTile
                declarer.hand.call(MeldType.CLOSED_KAN, kanTiles, source = null, direction = RelativeDirection.Self)
            }

            GameAction.KanType.ADDED_KAN -> {
                val targetMeldIndex = declarer.hand.exposedMelds.indexOfFirst {
                    it.type == MeldType.PON && it.tiles.first().tile.withoutRed == incomingTile.tile.withoutRed
                }
                declarer.hand.upgradeToAddedKan(incomingTile, targetMeldIndex)
            }

            GameAction.KanType.OPEN_KAN -> error("Unreachable: OPEN_KAN never reaches KanDeclarationApplier")
        }

        val (rinshanTile, newWall) = state.tileWall.drawLast()
        if (rinshanTile == null) {
            return Result(state, null)
        }

        val declarerAfterMeld = declarer.copy(hand = handAfterMeld).recordAction(kanAction)
        val declarerAfterDraw = declarerAfterMeld
            .copy(hand = declarerAfterMeld.hand.copy(lastDrawn = rinshanTile))
            .clearPassedTiles()
            .recordAction(GameAction.Draw)
        val playersAfterMeld = state.players.map { if (it.id == declarerId) declarerAfterDraw else it }
        val playersAfterMeldClaimed = module.onMeldClaimed(playersAfterMeld)

        return Result(state.copy(players = playersAfterMeldClaimed, tileWall = newWall), rinshanTile)
    }
}
