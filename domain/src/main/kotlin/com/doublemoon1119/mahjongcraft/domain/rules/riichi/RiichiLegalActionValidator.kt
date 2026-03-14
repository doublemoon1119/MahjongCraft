package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState

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
        val riichiState = player.playerRuleState as? RiichiPlayerState
        val isRiichiDeclared = riichiState?.isRiichiDeclared == true

        // incomingTile == null 表示玩家正在打牌（準備捨牌）
        // 捨牌動作由 UI 層處理，讓玩家選擇要打的牌
        // 此 Validator 只處理「額外動作」（鳴牌、胡牌、立直）
        if (incomingTile == null) {
            // 檢查是否可以立直 (Riichi)
            // 條件：向聽數為 0 且門前清（無副露）且未曾立直且點數 >= 1000
            if (!isRiichiDeclared && player.hand.exposedMelds.isEmpty() && player.score >= 1000) {
                val result = shantenCalculator.calculate(
                    Hand(player.hand.standingTiles.toMutableList())
                )
                if (result is ShantenResult.Tenpai) {
                    legalActions.add(GameAction.Riichi)
                }
            }

            return legalActions
        }

        // 取得進牌的基礎類型（忽略赤寶牌屬性）
        val incomingBaseTile = incomingTile.tile.stripRed()

        // 處理有 incomingTile 的情況
        if (source == RelativeDirection.Self) {
            // 自己摸牌
            // 1. 檢查是否可以自摸 (Tsumo)
            val tempHandTsumo = Hand((player.hand.standingTiles + incomingTile).toMutableList())
            val tsumoResult = shantenCalculator.calculate(tempHandTsumo)
            if (tsumoResult is ShantenResult.Complete) {
                legalActions.add(GameAction.Tsumo)
            }

            // 2. 檢查是否可以加槓 (Added Kan)
            player.hand.exposedMelds.forEach { meld ->
                if (meld.type == MeldType.PON && meld.tiles.first().tile.stripRed() == incomingBaseTile) {
                    legalActions.add(GameAction.Kan(GameAction.KanType.ADDED_KAN, incomingTile.id, emptyList()))
                }
            }

            // 3. 檢查是否可以暗槓 (Closed Kan)
            // 立直後暗槓限制：
            // - 暗槓前跟暗槓後聽的牌必須一模一樣才能暗槓
            // - 需要計算暗槓後的聽牌列表，與暗槓前的聽牌列表比對
            // TODO: 待向聽計算器支援聽牌分析後實作
            val closedKanCount = player.hand.standingTiles.count { it.tile.stripRed() == incomingBaseTile }
            if (closedKanCount == 3) {
                val withTiles = player.hand.standingTiles.filter { it.tile.stripRed() == incomingBaseTile }.map { it.id }
                legalActions.add(GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingTile.id, withTiles))
            }

        } else {
            // 他家打牌
            // 1. 檢查是否可以榮和 (Ron)
            val tempHandRon = Hand((player.hand.standingTiles + incomingTile).toMutableList())
            val ronResult = shantenCalculator.calculate(tempHandRon)

            if (ronResult is ShantenResult.Complete) {
                // 振聽檢查：
                // 1. 先檢查玩家在收到這張牌前是否已經聽牌
                // 2. 如果已經聽牌，檢查是否有打過這張牌（胡牌張）
                // 3. 有打過 → 振聽狀態，不能榮和
                // 4. 在當前巡迴中放過榮和（同巡振聽）
                val currentHandResult = shantenCalculator.calculate(
                    Hand(player.hand.standingTiles.toMutableList())
                )

                val isFuriten = if (currentHandResult is ShantenResult.Tenpai) {
                    // 取得玩家已打過的牌（忽略赤寶牌屬性）
                    val discardPile = player.discardPile
                    val discardedBaseTiles = discardPile.entries.map { it.tile.tile.stripRed() }

                    // 檢查是否打過這張牌（傳統振聽）
                    val hasDiscardedWinningTile = discardedBaseTiles.contains(incomingBaseTile)

                    // 檢查是否在當前巡迴中放過榮和（同巡振聽）
                    val hasPassedRon = player.isPassedTile(incomingBaseTile)

                    hasDiscardedWinningTile || hasPassedRon
                } else {
                    // 手牌原本未聽牌，不可能是振聽
                    false
                }

                if (!isFuriten) {
                    legalActions.add(GameAction.Ron(incomingTile.id))
                }
            }

            // 2. 檢查是否可以碰 (Pon)
            // 立直後不能碰
            // 赤寶牌與普通牌視為同一張牌，故使用 stripRed() 比較
            // 過水碰：若玩家在當前巡迴中已放過此牌，則不可碰
            val ponCount = player.hand.standingTiles.count { it.tile.stripRed() == incomingBaseTile }
            if (ponCount >= 2 && !isRiichiDeclared && !player.isPassedTile(incomingBaseTile)) {
                legalActions.add(GameAction.Pon(incomingTile.id))
            }

            // 3. 檢查是否可以吃 (Chi)
            // 立直後不能吃
            // 吃不受赤寶牌影響，但仍需使用 stripRed() 確保一致性
            if (source == RelativeDirection.Left && incomingTile.tile is Tile.Numeric && !isRiichiDeclared) {
                val iTile = incomingTile.tile
                val handTiles = player.hand.standingTiles

                // 3a. 檢查 (tile - 1, tile - 2) 的組合
                if (iTile.value > 2) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1, isRed = false)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value - 2, isRed = false)
                    val id1 = handTiles.find { it.tile.stripRed() == t1 }?.id
                    val id2 = handTiles.find { it.tile.stripRed() == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3b. 檢查 (tile - 1, tile + 1) 的組合
                if (iTile.value in 2..<9) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1, isRed = false)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 1, isRed = false)
                    val id1 = handTiles.find { it.tile.stripRed() == t1 }?.id
                    val id2 = handTiles.find { it.tile.stripRed() == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3c. 檢查 (tile + 1, tile + 2) 的組合
                if (iTile.value < 8) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value + 1, isRed = false)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 2, isRed = false)
                    val id1 = handTiles.find { it.tile.stripRed() == t1 }?.id
                    val id2 = handTiles.find { it.tile.stripRed() == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }
            }

            // 4. 檢查是否可以大明槓 (Open Kan)
            // 立直後不能明槓
            // 赤寶牌與普通牌視為同一張牌，故使用 stripRed() 比較
            val openKanCount = player.hand.standingTiles.count { it.tile.stripRed() == incomingBaseTile }
            if (openKanCount == 3 && !isRiichiDeclared) {
                val withTiles = player.hand.standingTiles.filter { it.tile.stripRed() == incomingBaseTile }.map { it.id }
                legalActions.add(GameAction.Kan(GameAction.KanType.OPEN_KAN, incomingTile.id, withTiles))
            }
        }

        return legalActions
    }
}

/**
 * 取得牌的基礎類型（忽略赤寶牌屬性）。
 *
 * 赤寶牌與普通牌在聽牌分析上視為同張牌，故進行比較時需忽略 [Tile.Numeric.isRed] 屬性。
 *
 * @return 不含赤寶牌屬性的牌副本，若非 [Tile.Numeric] 直接回傳原牌。
 */
private fun Tile.stripRed(): Tile = when (this) {
    is Tile.Numeric -> Tile.Numeric(suit, value, isRed = false)
    else -> this
}
