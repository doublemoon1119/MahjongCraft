package com.doublemoon1119.mahjongcraft.domain.rules.riichi

import com.doublemoon1119.mahjongcraft.domain.base.*
import com.doublemoon1119.mahjongcraft.domain.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenCalculator
import com.doublemoon1119.mahjongcraft.domain.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.HandYakuResult
import com.doublemoon1119.mahjongcraft.domain.rules.riichi.yaku.RiichiYakuContext
import com.doublemoon1119.mahjongcraft.domain.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.domain.table.TableState
import com.doublemoon1119.mahjongcraft.domain.util.isNumeric
import com.doublemoon1119.mahjongcraft.domain.util.withoutRed
import kotlin.math.abs

/**
 * 日本麻將規則的合法動作判定器。
 *
 * 負責根據立直麻將的規則（包含振聽、立直、槓牌限制等）分析玩家的合法動作。
 *
 * @property shantenCalculator 向聽數計算器，用於判斷聽牌與胡牌。
 * @property handValueCalculator 手牌役種計算機，用於檢查最低番數限制。
 */
class RiichiLegalActionValidator(
    private val shantenCalculator: ShantenCalculator,
    private val handValueCalculator: HandValueCalculator<RiichiYakuContext, HandYakuResult>
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
        val hand = player.hand
        val isMenzen = hand.exposedMelds.isEmpty() || hand.exposedMelds.all { it.type == MeldType.CLOSED_KAN }
        val riichiState = player.playerRuleState as? RiichiPlayerState
        val isRiichi = riichiState?.isRiichi == true

        // incomingTile == null 表示玩家正在打牌（準備捨牌）
        // 捨牌動作由 UI 層處理，讓玩家選擇要打的牌
        // 此 Validator 只處理「額外動作」（鳴牌、胡牌、立直）
        if (incomingTile == null) {
            // 檢查是否可以立直 (Riichi)
            // 條件：向聽數為 0 且門前清（無副露）且未曾立直且點數 >= 1000
            if (!isRiichi && isMenzen && player.score >= 1000) {
                val result = shantenCalculator.calculate(
                    Hand(
                        player.hand.standingTiles.toMutableList(),
                        player.hand.exposedMelds.toMutableList()
                    )
                )
                if (result is ShantenResult.Tenpai) {
                    legalActions.add(GameAction.Riichi)
                }
            }

            return legalActions
        }

        // 取得進牌的基礎類型（忽略赤寶牌屬性）
        val incomingBaseTile = incomingTile.tile.withoutRed

        // 處理有 incomingTile 的情況
        if (source == RelativeDirection.Self) {
            // 自己摸牌
            // 1. 檢查是否可以自摸 (Tsumo)
            val tempHandTsumo = Hand(
                (player.hand.standingTiles + incomingTile).toMutableList(),
                player.hand.exposedMelds.toMutableList()
            )
            val tsumoResult = shantenCalculator.calculate(tempHandTsumo)
            if (tsumoResult is ShantenResult.Complete) {
                if (checkMinimumHan(
                        tableState = tableState,
                        player = player,
                        incomingTile = incomingTile,
                        isTsumo = true
                    )
                ) {
                    legalActions.add(GameAction.Tsumo)
                }
            }

            // 2. 檢查是否可以加槓 (Added Kan)
            player.hand.exposedMelds.forEach { meld ->
                if (meld.type == MeldType.PON && meld.tiles.first().tile.withoutRed == incomingBaseTile) {
                    legalActions.add(GameAction.Kan(GameAction.KanType.ADDED_KAN, incomingTile.id, emptyList()))
                }
            }

            // 3. 檢查是否可以暗槓 (Closed Kan)
            // 立直後暗槓限制：
            // - 暗槓前跟暗槓後聽的牌必須一模一樣才能暗槓
            // - 需要計算暗槓後的聽牌列表，與暗槓前的聽牌列表比對
            val closedKanCount = player.hand.standingTiles.count { it.tile.withoutRed == incomingBaseTile }
            if (closedKanCount == 3) {
                val withTiles =
                    player.hand.standingTiles.filter { it.tile.withoutRed == incomingBaseTile }.map { it.id }

                // 若已宣告立直，檢查暗槓後聽牌是否不變
                if (isRiichi) {
                    if (checkClosedKanAfterRiichi(player, incomingTile)) {
                        legalActions.add(GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingTile.id, withTiles))
                    }
                } else {
                    // 未立直，正常允許暗槓
                    legalActions.add(GameAction.Kan(GameAction.KanType.CLOSED_KAN, incomingTile.id, withTiles))
                }
            }

        } else {
            // 他家打牌
            // 1. 檢查是否可以榮和 (Ron)
            val tempHandRon = Hand(
                (player.hand.standingTiles + incomingTile).toMutableList(),
                player.hand.exposedMelds.toMutableList()
            )
            val ronResult = shantenCalculator.calculate(tempHandRon)

            if (ronResult is ShantenResult.Complete) {
                // 振聽檢查：
                // 1. 先檢查玩家在收到這張牌前是否已經聽牌
                // 2. 如果已經聽牌，檢查這張牌是否在振聽列表中
                val currentHandResult = shantenCalculator.calculate(
                    Hand(
                        player.hand.standingTiles.toMutableList(),
                        player.hand.exposedMelds.toMutableList()
                    )
                )

                val isFuriten = if (currentHandResult is ShantenResult.Tenpai) {
                    val furitenTiles = riichiState?.getFuritenTiles(
                        discardPile = player.discardPile,
                        passedTilesInRound = player.passedTilesInRound
                    ) ?: emptySet()

                    furitenTiles.contains(incomingBaseTile)
                } else {
                    // 手牌原本未聽牌，不可能是振聽
                    false
                }

                if (!isFuriten) {
                    if (checkMinimumHan(
                            tableState = tableState,
                            player = player,
                            incomingTile = incomingTile,
                            isTsumo = false
                        )
                    ) {
                        legalActions.add(GameAction.Ron(incomingTile.id))
                    }
                }
            }

            // 2. 檢查是否可以碰 (Pon)
            // 立直後不能碰
            // 赤寶牌與普通牌視為同一張牌，故使用 withoutRed 比較
            // 過水碰：若玩家在當前巡迴中已放過此牌，則不可碰
            val ponCount = player.hand.standingTiles.count { it.tile.withoutRed == incomingBaseTile }
            if (ponCount >= 2 && !isRiichi && incomingBaseTile !in player.passedTilesInRound) {
                legalActions.add(GameAction.Pon(incomingTile.id))
            }

            // 3. 檢查是否可以吃 (Chi)
            // 立直後不能吃
            // 吃不受赤寶牌影響，但仍需使用 withoutRed 確保一致性
            if (source == RelativeDirection.Left && incomingTile.tile is Tile.Numeric && !isRiichi) {
                val iTile = incomingTile.tile
                val handTiles = player.hand.standingTiles

                // 3a. 檢查 (tile - 1, tile - 2) 的組合
                if (iTile.value > 2) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1, isRed = false)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value - 2, isRed = false)
                    val id1 = handTiles.find { it.tile.withoutRed == t1 }?.id
                    val id2 = handTiles.find { it.tile.withoutRed == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3b. 檢查 (tile - 1, tile + 1) 的組合
                if (iTile.value in 2..<9) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1, isRed = false)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 1, isRed = false)
                    val id1 = handTiles.find { it.tile.withoutRed == t1 }?.id
                    val id2 = handTiles.find { it.tile.withoutRed == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3c. 檢查 (tile + 1, tile + 2) 的組合
                if (iTile.value < 8) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value + 1, isRed = false)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 2, isRed = false)
                    val id1 = handTiles.find { it.tile.withoutRed == t1 }?.id
                    val id2 = handTiles.find { it.tile.withoutRed == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }
            }

            // 4. 檢查是否可以大明槓 (Open Kan)
            // 立直後不能明槓
            // 赤寶牌與普通牌視為同一張牌，故使用 withoutRed 比較
            val openKanCount = player.hand.standingTiles.count { it.tile.withoutRed == incomingBaseTile }
            if (openKanCount == 3 && !isRiichi) {
                val withTiles =
                    player.hand.standingTiles.filter { it.tile.withoutRed == incomingBaseTile }.map { it.id }
                legalActions.add(GameAction.Kan(GameAction.KanType.OPEN_KAN, incomingTile.id, withTiles))
            }
        }

        // 若有其他合法動作，允許玩家選擇放棄
        if (legalActions.isNotEmpty()) {
            legalActions.add(GameAction.Pass)
        }

        return legalActions
    }

    /**
     * 檢查手牌是否符合最低胡牌番數限制。
     *
     * @param tableState 當前的遊戲桌況。
     * @param player 欲判斷合法動作的玩家。
     * @param incomingTile 進來的牌（胡牌的那張牌）。
     * @param isTsumo 是否為自摸。
     * @return 是否符合最低番數限制。
     */
    private fun checkMinimumHan(
        tableState: TableState,
        player: MahjongPlayer,
        incomingTile: IdentifiedTile,
        isTsumo: Boolean
    ): Boolean {
        val minimumWinConstraint = tableState.config.minimumWinConstraint
        if (minimumWinConstraint <= 0) {
            return true
        }

        val roundWind = tableState.prevalentWind
        val seatWind = player.currentWind
        val hand = player.hand
        val isMenzen = hand.exposedMelds.isEmpty() || hand.exposedMelds.all { it.type == MeldType.CLOSED_KAN }
        val riichiState = player.playerRuleState as? RiichiPlayerState
        val isRiichi = riichiState?.isRiichi == true
        val isDoubleRiichi = riichiState?.isDoubleRiichi == true
        val isIppatsu = riichiState?.isIppatsu == true

        // TODO: 補齊 Context
        val context = RiichiYakuContext(
            hand = hand,
            winningTile = incomingTile.tile,
            isTsumo = isTsumo,
            isMenzen = isMenzen,
            roundWind = roundWind,
            seatWind = seatWind,
            isRiichi = isRiichi,
            isDoubleRiichi = isDoubleRiichi,
            isIppatsu = isIppatsu
        )

        val result = handValueCalculator.calculate(context)

        // 役滿（totalValue < 0）必定滿足最低番數限制
        if (result.totalValue < 0) {
            return true
        }

        return result.totalValue >= minimumWinConstraint
    }

    /**
     * 檢查是否允許立直後暗槓。
     *
     * 需要符合以下條件：
     * 1. 暗槓後面子結構不可改變
     * 2. 暗槓前與暗槓後的聽牌列表必須完全相同才能暗槓。
     *
     * @param player 玩家。
     * @param incomingTile 即将暗槓的牌。
     * @return 是否允許暗槓。
     */
    private fun checkClosedKanAfterRiichi(player: MahjongPlayer, incomingTile: IdentifiedTile): Boolean {
        val incomingBaseTile = incomingTile.tile.withoutRed

        // 如果這 4 張牌暗槓與其他順子有連結，直接回傳 false
        if (isMeldStructureChanged(player.hand.standingTiles, incomingTile)) {
            return false
        }

        // 取得暗槓前的聽牌列表
        val currentHand = Hand(
            player.hand.standingTiles.toMutableList(),
            player.hand.exposedMelds.toMutableList()
        )
        val beforeKanResult = shantenCalculator.calculate(currentHand)

        if (beforeKanResult !is ShantenResult.Tenpai) {
            // 暗槓前未聽牌，理論上不應進入此分支（呼叫端已檢查）
            return false
        }

        val winningTilesBefore = beforeKanResult.winningTiles.map { it.withoutRed }.toSet()

        // 暗槓模擬：將手牌中 3 張相同的牌移至副露區，再加上摸到的牌湊成 4 張牌
        val tilesToKan = player.hand.standingTiles.filter { it.tile.withoutRed == incomingBaseTile }.toMutableList()
        tilesToKan.add(incomingTile)
        val remainingTiles = player.hand.standingTiles.filter { it.tile.withoutRed != incomingBaseTile }

        // 建立暗槓的 Meld
        val closedKanMeld = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = tilesToKan,
            sourceTile = null,
            sourceDirection = RelativeDirection.Self
        )

        // 建立新的 Hand，包含剩余立牌和新增的暗槓
        val meldsAfterKan = (player.hand.exposedMelds + closedKanMeld).toMutableList()
        val handAfterKan = Hand(remainingTiles.toMutableList(), meldsAfterKan)

        // 取得暗槓後的聽牌列表
        val afterKanResult = shantenCalculator.calculate(handAfterKan)

        if (afterKanResult !is ShantenResult.Tenpai) {
            // 暗槓後未聽牌，不允許暗槓
            return false
        }

        val winningTilesAfter = afterKanResult.winningTiles.map { it.withoutRed }.toSet()

        // 比較兩者的聽牌列表是否完全相同
        return winningTilesBefore == winningTilesAfter
    }

    /**
     * 檢查暗槓是否會改變面子結構 (日麻立直專用規則)。
     *
     * 規則：被槓掉的四張牌，在原本的手牌解釋中，必須「只能」是刻子。
     * 如果這四張牌與手牌中其他牌存在數字連結（1, 2格內），則視為結構改變。
     */
    private fun isMeldStructureChanged(standingTiles: List<IdentifiedTile>, kanTile: IdentifiedTile): Boolean {
        // 轉換換成數牌，非數牌(字牌)沒有順子問題，只要聽牌不變，字牌暗槓永遠合法
        val kanBase = kanTile.tile.withoutRed as? Tile.Numeric? ?: return false

        // 取得除去這組刻子後，剩下的立牌
        val otherTiles = standingTiles.filter { it.tile.withoutRed != kanBase }

        // 檢查剩下的牌中，有沒有與槓牌同花色且數字距離在 2 以內的牌
        // 例如：槓 2 萬，手牌有 1, 3, 4 萬，則代表 2 萬具有組成順子的「血緣關係」
        return otherTiles.any {
            val otherBase = it.tile.withoutRed as? Tile.Numeric? ?: return@any false

            otherBase.isNumeric &&
                    otherBase.suit == kanBase.suit &&
                    abs(otherBase.value - kanBase.value) <= 2
        }
    }
}
