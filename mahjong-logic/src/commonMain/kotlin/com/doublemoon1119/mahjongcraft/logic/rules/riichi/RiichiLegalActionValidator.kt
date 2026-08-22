package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.judgment.LegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.util.isHonor
import com.doublemoon1119.mahjongcraft.logic.util.isNumeric
import com.doublemoon1119.mahjongcraft.logic.util.isTerminal
import kotlin.math.abs

/**
 * 日本麻將規則的合法動作判定器。
 *
 * 負責根據立直麻將的規則（包含振聽、立直、槓牌限制等）分析玩家的合法動作。
 *
 * @property shantenCalculator 向聽數計算器，用於判斷聽牌與胡牌。
 * @property handValueCalculator 手牌役種計算機，用於檢查最低番數限制。
 * @property contextCalculator 手牌役種上下文計算機，用於計算寶牌、海底撈月等資訊。
 */
class RiichiLegalActionValidator(
    private val shantenCalculator: RiichiShantenCalculator,
    private val handValueCalculator: RiichiHandValueCalculator,
    private val contextCalculator: RiichiHandValueContextCalculator,
) : LegalActionValidator {

    /**
     * 判斷在當前遊戲狀態下，指定玩家可以執行的合法動作列表。
     *
     * @param tableState 當前的遊戲桌況。
     * @param player 欲判斷合法動作的玩家。
     * @param sourceAction 觸發此判斷的動作。
     * @param sourceDirection 動作的來源方位。
     * @param incomingTile 可選參數，表示剛摸到或他家打出的牌。
     * @return 該玩家可以執行的合法動作列表。
     */
    override fun getLegalActions(
        tableState: TableState,
        player: MahjongPlayer,
        sourceAction: GameAction,
        sourceDirection: RelativeDirection,
        incomingTile: IdentifiedTile?,
    ): List<GameAction> {
        val legalActions = mutableListOf<GameAction>()
        val hand = player.hand
        val isMenzen = hand.exposedMelds.isEmpty() || hand.exposedMelds.all { it.type == MeldType.CLOSED_KAN }
        val riichiState = player.playerRuleState as? RiichiPlayerState
        val isRiichi = riichiState?.isRiichi == true

        // 全場槓子數上限（明槓/暗槓/加槓皆算）固定 4 次，不論這 4 次是不是同一位玩家達成——「同一位
        // 玩家獨得全部 4 槓」只是不觸發 resolveSuukanNagare 流局判定（見該函式 KDoc，可能正在嘗試
        // 四槓子役滿），不代表這位玩家還能繼續槓第 5 次；已達上限時完全不提供任何一種槓的候選，之後
        // 由 GameFlowCoordinator 在下一次捨牌/立直前檢查 resolveSuukanNagare 決定是否流局。
        val totalKanCount = tableState.players.sumOf { p ->
            p.hand.exposedMelds.count {
                it.type == MeldType.OPEN_KAN || it.type == MeldType.ADDED_KAN || it.type == MeldType.CLOSED_KAN
            }
        }
        val canDeclareAnotherKan = totalKanCount < MAX_KAN_COUNT

        // 牌山（不含王牌）是否還有牌可摸——恆等於這次判斷當下 tableState.tileWall.remainingCount。
        // == 0 有兩種情境：(1) sourceDirection == Self 時，incomingTile 剛好是海底牌本身（摸完它之後
        // 牌山正好摸盡），日麻規則海底牌不能拿來暗槓／加槓；(2) 反應他家捨牌時，這張捨牌是海底牌
        // 摸盡後打出的河底牌，只能榮和（河底撈魚）或流局，不能吃/碰/大明槓——因為吃/碰之後下一位
        // 仍須有正常摸牌機會，大明槓還涉及嶺上補牌，這個時間點都已經不成立。
        val wallHasMoreTiles = tableState.tileWall.remainingCount > 0

        // incomingTile == null 表示玩家正在打牌（準備捨牌）
        // 捨牌動作由 UI 層處理，讓玩家選擇要打的牌
        // 此 Validator 只處理「額外動作」（鳴牌、胡牌、立直）
        if (incomingTile == null) {
            // 檢查是否可以立直 (Riichi)
            // 條件：向聽數為 0 且門前清（無副露）且未曾立直且點數 >= 1000，且牌山剩餘張數至少還夠
            // 自己再摸一次（>= 玩家人數，確保輪到自己之前不會被其他人摸盡）
            if (!isRiichi && isMenzen && player.score >= 1000 && tableState.tileWall.remainingCount >= tableState.playerCount) {
                val result = shantenCalculator.calculate(
                    Hand(
                        player.hand.standingTiles.toMutableList(),
                        player.hand.exposedMelds.toMutableList(),
                    ),
                )
                if (result is ShantenResult.Tenpai) {
                    legalActions.add(GameAction.Riichi)
                }
            }

            return legalActions
        }

        // 取得進牌的基礎類型（忽略赤寶牌屬性）
        val incomingBaseTile = incomingTile.tile.riichiCanonical

        // 處理有 incomingTile 的情況
        if (sourceDirection == RelativeDirection.Self) {
            // 自己摸牌
            // 1. 檢查是否可以自摸 (Tsumo)
            val tempHandTsumo = Hand(
                (player.hand.standingTiles + incomingTile).toMutableList(),
                player.hand.exposedMelds.toMutableList(),
            )
            val tsumoResult = shantenCalculator.calculate(tempHandTsumo)
            if (tsumoResult is ShantenResult.Complete) {
                if (checkMinimumHan(
                        tableState = tableState,
                        player = player,
                        incomingTile = incomingTile,
                        isTsumo = true,
                    )
                ) {
                    legalActions.add(GameAction.Tsumo)
                }
            }

            // 2. 檢查是否可以加槓 (Added Kan)——海底牌不能拿來加槓
            if (canDeclareAnotherKan && wallHasMoreTiles) {
                player.hand.exposedMelds.forEach { meld ->
                    if (meld.type == MeldType.PON && meld.tiles.first().tile.riichiCanonical == incomingBaseTile) {
                        legalActions.add(GameAction.Kan(GameAction.KanType.ADDED_KAN, incomingTile.id, emptyList()))
                    }
                }
            }

            // 3. 檢查是否可以暗槓 (Closed Kan)
            // 立直後暗槓限制：
            // - 暗槓前跟暗槓後聽的牌必須一模一樣才能暗槓
            // - 需要計算暗槓後的聽牌列表，與暗槓前的聽牌列表比對
            val closedKanCount = player.hand.standingTiles.count { it.tile.riichiCanonical == incomingBaseTile }
            if (canDeclareAnotherKan && wallHasMoreTiles && closedKanCount == 3) {
                val withTiles =
                    player.hand.standingTiles.filter { it.tile.riichiCanonical == incomingBaseTile }.map { it.id }

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

            // 4. 檢查是否可以宣告和局 (九種九牌)
            if (tableState.isFirstGoAround(player)) {
                val isKyuushuKyuuhai = (player.hand.standingTiles + incomingTile)
                    .filter { it.tile.isTerminal || it.tile.isHonor } // 過濾么九牌
                    .map { it.tile.riichiCanonical }
                    .toSet().size >= 9
                if (isKyuushuKyuuhai) {
                    legalActions.add(GameAction.ExhaustiveDraw(RiichiExhaustiveDrawReason.KyuushuKyuuhai))
                }
            }
        } else {
            // 他家打牌
            // 1. 檢查是否可以榮和 (Ron)
            val tempHandRon = Hand(
                (player.hand.standingTiles + incomingTile).toMutableList(),
                player.hand.exposedMelds.toMutableList(),
            )
            val ronResult = shantenCalculator.calculate(tempHandRon)

            if (ronResult is ShantenResult.Complete) {
                // 振聽檢查：
                // 1. 先檢查玩家在收到這張牌前是否已經聽牌
                // 2. 如果已經聽牌，檢查這張牌是否在振聽列表中
                val currentHandResult = shantenCalculator.calculate(
                    Hand(
                        player.hand.standingTiles.toMutableList(),
                        player.hand.exposedMelds.toMutableList(),
                    ),
                )

                val isFuriten = if (riichiState?.isPermanentlyFuriten == true) {
                    // 立直後放過和牌機會即永久振聽，本局結束前恆為振聽，不需要再比對聽牌面或
                    // passedTilesInRound——那些之後可能因為摸牌清空，但永久振聽不受影響。
                    true
                } else if (currentHandResult is ShantenResult.Tenpai) {
                    val furitenTiles = riichiState?.getFuritenTiles(
                        discardPile = player.discardPile,
                        passedTilesInRound = player.passedTilesInRound,
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
                            isTsumo = false,
                            isRobbingKan = sourceAction is GameAction.Kan && sourceAction.type == GameAction.KanType.ADDED_KAN,
                            isRobbingClosedKan = sourceAction is GameAction.Kan && sourceAction.type == GameAction.KanType.CLOSED_KAN,
                        )
                    ) {
                        legalActions.add(GameAction.Ron(incomingTile.id))
                    }
                }
            }

            // 2. 檢查是否可以碰 (Pon)
            // 立直後不能碰、河底牌不能碰
            // 赤五與普通五視為同一張牌，故使用日麻標準牌比較
            // 過水碰：若玩家在當前巡迴中已放過此牌，則不可碰
            val ponCount = player.hand.standingTiles.count { it.tile.riichiCanonical == incomingBaseTile }
            if (ponCount >= 2 && !isRiichi && wallHasMoreTiles && incomingBaseTile !in player.passedTilesInRound) {
                legalActions.add(GameAction.Pon(incomingTile.id))
            }

            // 3. 檢查是否可以吃 (Chi)
            // 立直後不能吃、河底牌不能吃
            // 吃不受赤五外觀影響，必須使用日麻標準牌判斷數值與花色
            val incomingNumeric = incomingBaseTile as? Tile.Numeric
            if (sourceDirection == RelativeDirection.Left && incomingNumeric != null && !isRiichi && wallHasMoreTiles) {
                val iTile = incomingNumeric
                val handTiles = player.hand.standingTiles

                // 3a. 檢查 (tile - 1, tile - 2) 的組合
                if (iTile.value > 2) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value - 2)
                    val id1 = handTiles.find { it.tile.riichiCanonical == t1 }?.id
                    val id2 = handTiles.find { it.tile.riichiCanonical == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3b. 檢查 (tile - 1, tile + 1) 的組合
                if (iTile.value in 2..<9) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value - 1)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 1)
                    val id1 = handTiles.find { it.tile.riichiCanonical == t1 }?.id
                    val id2 = handTiles.find { it.tile.riichiCanonical == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }

                // 3c. 檢查 (tile + 1, tile + 2) 的組合
                if (iTile.value < 8) {
                    val t1 = Tile.Numeric(iTile.suit, iTile.value + 1)
                    val t2 = Tile.Numeric(iTile.suit, iTile.value + 2)
                    val id1 = handTiles.find { it.tile.riichiCanonical == t1 }?.id
                    val id2 = handTiles.find { it.tile.riichiCanonical == t2 }?.id
                    if (id1 != null && id2 != null) {
                        legalActions.add(GameAction.Chi(incomingTile.id, listOf(id1, id2)))
                    }
                }
            }

            // 4. 檢查是否可以大明槓 (Open Kan)
            // 立直後不能明槓、河底牌不能明槓
            // 赤五與普通五視為同一張牌，故使用日麻標準牌比較
            val openKanCount = player.hand.standingTiles.count { it.tile.riichiCanonical == incomingBaseTile }
            if (canDeclareAnotherKan && wallHasMoreTiles && openKanCount == 3 && !isRiichi) {
                val withTiles =
                    player.hand.standingTiles.filter { it.tile.riichiCanonical == incomingBaseTile }.map { it.id }
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
     * @param isRobbingKan 是否為搶槓。
     * @param isRobbingClosedKan 是否為搶暗槓。
     * @return 是否符合最低番數限制。
     */
    private fun checkMinimumHan(
        tableState: TableState,
        player: MahjongPlayer,
        incomingTile: IdentifiedTile,
        isTsumo: Boolean,
        isRobbingKan: Boolean = false,
        isRobbingClosedKan: Boolean = false,
    ): Boolean {
        val minimumWinConstraint = tableState.config.minimumWinConstraint
        if (minimumWinConstraint <= 0) {
            return true
        }

        val context = contextCalculator.calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = incomingTile,
                isTsumo = isTsumo,
                isRobbingKan = isRobbingKan,
            ),
        )

        val result = handValueCalculator.calculate(context)

        // 役滿（totalHan < 0）
        if (result.totalHan < 0) {
            // 判斷是否為國士無雙
            val isKokushiMusou =
                result.yakuResults.any { it.yaku == YakuType.KokushiMusou || it.yaku == YakuType.KokushiMusou13 }

            return if (isRobbingClosedKan) {
                // 國士無雙才可以搶暗槓
                isKokushiMusou
            } else {
                // 溢滿必定滿足最低番數限制
                true
            }
        }

        // 非役滿不能搶暗槓
        if (isRobbingClosedKan) {
            return false
        }

        // 寶牌/裏寶牌/赤寶牌不計入最低番數限制——這裡只判斷「役種本身」夠不夠格胡牌，寶牌只在
        // result.totalHan（用來算實際點數）裡才該疊加，不能讓一手光靠寶牌湊到門檻的牌型被視為合法。
        val hanExcludingDora = result.yakuResults
            .filterNot { it.yaku == YakuType.Dora || it.yaku == YakuType.UraDora || it.yaku == YakuType.AkaDora }
            .sumOf { it.han }
        return hanExcludingDora >= minimumWinConstraint
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
        val incomingBaseTile = incomingTile.tile.riichiCanonical

        // 如果這 4 張牌暗槓與其他順子有連結，直接回傳 false
        if (isMeldStructureChanged(player.hand.standingTiles, incomingTile)) {
            return false
        }

        // 取得暗槓前的聽牌列表
        val currentHand = Hand(
            player.hand.standingTiles.toMutableList(),
            player.hand.exposedMelds.toMutableList(),
        )
        val beforeKanResult = shantenCalculator.calculate(currentHand)

        if (beforeKanResult !is ShantenResult.Tenpai) {
            // 暗槓前未聽牌，理論上不應進入此分支（呼叫端已檢查）
            return false
        }

        val winningTilesBefore = beforeKanResult.winningTiles.map { it.riichiCanonical }.toSet()

        // 暗槓模擬：將手牌中 3 張相同的牌移至副露區，再加上摸到的牌湊成 4 張牌
        val tilesToKan = player.hand.standingTiles.filter { it.tile.riichiCanonical == incomingBaseTile }.toMutableList()
        tilesToKan.add(incomingTile)
        val remainingTiles = player.hand.standingTiles.filter { it.tile.riichiCanonical != incomingBaseTile }

        // 建立暗槓的 Meld
        val closedKanMeld = Meld(
            type = MeldType.CLOSED_KAN,
            tiles = tilesToKan,
            sourceTile = null,
            sourceDirection = RelativeDirection.Self,
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

        val winningTilesAfter = afterKanResult.winningTiles.map { it.riichiCanonical }.toSet()

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
        val kanBase = kanTile.tile.riichiCanonical as? Tile.Numeric? ?: return false

        // 取得除去這組刻子後，剩下的立牌
        val otherTiles = standingTiles.filter { it.tile.riichiCanonical != kanBase }

        // 檢查剩下的牌中，有沒有與槓牌同花色且數字距離在 2 以內的牌
        // 例如：槓 2 萬，手牌有 1, 3, 4 萬，則代表 2 萬具有組成順子的「血緣關係」
        return otherTiles.any {
            val otherBase = it.tile.riichiCanonical as? Tile.Numeric? ?: return@any false

            otherBase.isNumeric &&
                otherBase.suit == kanBase.suit &&
                abs(otherBase.value - kanBase.value) <= 2
        }
    }

    private companion object {
        /** 全場槓子數上限（明槓/暗槓/加槓皆算），見 [getLegalActions] 對這個上限的說明。 */
        const val MAX_KAN_COUNT = 4
    }
}
