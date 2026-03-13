package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import java.util.*

/**
 * 日本麻將規則的合法動作判定器。
 *
 * 負責根據立直麻將的規則（包含振聽、立直、槓牌限制等）分析玩家的合法動作。
 *
 * @property shantenCalculator 向聽數計算器，用於判斷聽牌與胡牌。
 */
class RiichiLegalActionValidator(
    private val shantenCalculator: ShantenCalculator
) : LegalActionValidator {
    /**
     * 判斷在當前遊戲狀態下，指定玩家可以執行的合法動作列表。
     *
     * @param tableState 當前的遊戲桌況。
     * @param player 欲判斷合法動作的玩家。
     * @param source 動作的來源方位。
     * @param incomingTile 可選參數，表示剛摸到或他家打出的牌。
     * @return 該玩家可以執行的合法動作列表。
     */
    override fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        source: RelativeDirection,
        incomingTile: IdentifiedTile?
    ): List<GameAction> {
        val legalActions = mutableListOf<GameAction>()

        // TODO: 實裝打牌階段邏輯
        // - incomingTile == null 表示玩家正在打牌（準備捨牌）
        // - 此時應檢查：
        //   1. 是否可以立直 (Riichi)：向聽數為 0 且門前清（無副露）且未曾立直
        //   2. 一般捨牌選項（打出手牌中的任何一張牌）
        // - 立直後限制：
        //   - 不能吃、碰、明槓
        //   - 只能摸切（打剛摸到的牌）
        if (incomingTile == null) {
            return legalActions
        }

        // 處理有 incomingTile 的情況
        if (source == RelativeDirection.Self) {
            // 自己摸牌
            // 1. 檢查是否可以自摸 (Tsumo)
            val tempHandTsumo = Hand((player.hand.standingTiles + incomingTile).toMutableList())
            if (shantenCalculator.calculate(tempHandTsumo).shanten == -1) {
                legalActions.add(GameAction.Tsumo)
            }

            // 2. 檢查是否可以加槓 (Added Kan)
            player.hand.exposedMelds.forEach { meld ->
                if (meld.type == MeldType.PON && meld.tiles.first().tile == incomingTile.tile) {
                    legalActions.add(GameAction.Kan(GameAction.KanType.ADDED_KAN, incomingTile.id, emptyList()))
                }
            }

            // 3. 檢查是否可以暗槓 (Closed Kan)
            val closedKanCount = player.hand.standingTiles.count { it.tile == incomingTile.tile }
            if (closedKanCount == 3) {
                val withTiles = player.hand.standingTiles.filter { it.tile == incomingTile.tile }.map { it.id }
                legalActions.add(GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingTile.id, withTiles))
            }

        } else {
            // 他家打牌
            // 1. 檢查是否可以榮和 (Ron)
            // TODO: 振聽檢查
            // - 如果玩家已經聽牌，檢查是否打過相同的牌
            // - 如果打過（振聽），則不可榮和
            // - 需記錄玩家打過的牌（可從 discardPile 取得）
            val tempHandRon = Hand((player.hand.standingTiles + incomingTile).toMutableList())
            if (shantenCalculator.calculate(tempHandRon).shanten == -1) {
                legalActions.add(GameAction.Ron(incomingTile.id))
            }

            // 2. 檢查是否可以碰 (Pon)
            val ponCount = player.hand.standingTiles.count { it.tile == incomingTile.tile }
            if (ponCount >= 2) {
                legalActions.add(GameAction.Pon(incomingTile.id))
            }

            // 3. 檢查是否可以吃 (Chi)
            if (source == RelativeDirection.Left && incomingTile.tile is Tile.Numeric) {
                val iTile = incomingTile.tile
                val handTiles = player.hand.standingTiles

                // 3a. 檢查 (tile - 1, tile - 2) 的組合
                if (iTile.value > 2) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value - 2)
                    val id1 = handTiles.find { it.tile == t1 }?.id
                    val id2 = handTiles.find { it.tile == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3b. 檢查 (tile - 1, tile + 1) 的組合
                if (iTile.value > 1 && iTile.value < 9) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 1)
                    val id1 = handTiles.find { it.tile == t1 }?.id
                    val id2 = handTiles.find { it.tile == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3c. 檢查 (tile + 1, tile + 2) 的組合
                if (iTile.value < 8) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value + 1)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 2)
                    val id1 = handTiles.find { it.tile == t1 }?.id
                    val id2 = handTiles.find { it.tile == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }
            }

            // 4. 檢查是否可以大明槓 (Open Kan)
            val openKanCount = player.hand.standingTiles.count { it.tile == incomingTile.tile }
            if (openKanCount == 3) {
                val withTiles = player.hand.standingTiles.filter { it.tile == incomingTile.tile }.map { it.id }
                legalActions.add(GameAction.Kan(GameAction.KanType.OPEN_KAN, incomingTile.id, withTiles))
            }
        }

        return legalActions
    }
}
