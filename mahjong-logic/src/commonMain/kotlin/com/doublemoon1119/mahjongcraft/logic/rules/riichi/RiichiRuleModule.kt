package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RiichiDeclarationResult
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 日本麻將規則模組實作。
 *
 * 負責串接日本麻將特有的組件，包含 [RiichiWallFactory] 與 [RiichiDiscardPile]...等等。
 */
class RiichiRuleModule(
    override val id: String,
    override val config: RiichiRuleConfig,
) : MahjongRuleModule<RiichiRuleConfig> {
    /**
     * 建立日本麻將牌山工廠。
     *
     * @return [RiichiWallFactory] 實體。
     */
    override fun createWallFactory(): RiichiWallFactory = RiichiWallFactory(config)

    /**
     * 建立日本麻將專用的牌河。
     *
     * @return [RiichiDiscardPile] 實體。
     */
    override fun createDiscardPile(): RiichiDiscardPile = RiichiDiscardPile()

    /**
     * 建立日本麻將的向聽數計算器。
     *
     * @return [RiichiShantenCalculator] 實體。
     */
    override fun createShantenCalculator(): RiichiShantenCalculator = RiichiShantenCalculator()

    /**
     * 建立日本麻將的合法動作判定器。
     *
     * @return [RiichiLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(): RiichiLegalActionValidator = RiichiLegalActionValidator(
        shantenCalculator = createShantenCalculator(),
        handValueCalculator = createHandValueCalculator(),
        contextCalculator = createHandValueContextCalculator(),
    )

    /**
     * 建立日本麻將的手牌價值計算機。
     *
     * @return [RiichiHandValueCalculator] 實體。
     */
    override fun createHandValueCalculator(): RiichiHandValueCalculator = RiichiHandValueCalculator(useLocalYaku = config.useLocalYaku)

    /**
     * 建立日本麻將的手牌價值上下文計算機。
     *
     * @return [RiichiHandValueContextCalculator] 實體。
     */
    override fun createHandValueContextCalculator(): RiichiHandValueContextCalculator = RiichiHandValueContextCalculator(config)

    /**
     * 建立日本麻將的初始動態桌況狀態。
     *
     * @return 全新的 [RiichiDynamicState]（立直棒數量為 0）。
     */
    override fun createInitialDynamicState(): RiichiDynamicState = RiichiDynamicState()

    /**
     * 建立日本麻將的初始玩家規則狀態。
     *
     * @return 全新的 [RiichiPlayerState]（尚未立直、無包牌責任）。
     */
    override fun createInitialPlayerRuleState(): RiichiPlayerState = RiichiPlayerState()

    /**
     * 套用日本麻將立直宣告的狀態變化：標記捨牌紀錄、更新立直/雙立直/一發狀態、立直棒 +1。
     *
     * @return 套用宣告後的新玩家實例與新的 [RiichiDynamicState]；若 [player]/[tableState] 缺少
     *         日麻所需的規則狀態（理論上不會發生，僅作防呆）則回傳 null。
     */
    override fun declareRiichi(
        tableState: TableState,
        player: MahjongPlayer,
        discardResult: Hand.DiscardResult,
    ): RiichiDeclarationResult? {
        val riichiState = player.playerRuleState as? RiichiPlayerState ?: return null
        val riichiDiscardPile = player.discardPile as? RiichiDiscardPile ?: return null
        val riichiDynamicState = tableState.dynamicRuleState as? RiichiDynamicState ?: return null

        val isDoubleRiichi = tableState.isFirstGoAround(player)
        val updatedPlayerRuleState = riichiState.copy(
            riichiTile = if (isDoubleRiichi) null else discardResult.tile,
            doubleRiichiTile = if (isDoubleRiichi) discardResult.tile else null,
            isIppatsu = true,
        )
        val updatedPlayer = player.copy(
            hand = discardResult.hand,
            discardPile = riichiDiscardPile.discard(RiichiDiscardEntry(discardResult.tile, isRiichi = true)),
            score = player.score - 1000,
            playerRuleState = updatedPlayerRuleState,
        )

        return RiichiDeclarationResult(
            player = updatedPlayer,
            dynamicRuleState = riichiDynamicState.copy(riichiStickCount = riichiDynamicState.riichiStickCount + 1),
        )
    }

    /**
     * 若玩家已立直且仍在一發窗口內，摸牌代表這個窗口已經結束（本巡未能胡牌），故清除一發資格。
     */
    override fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer {
        val riichiState = player.playerRuleState as? RiichiPlayerState ?: return player
        if (!riichiState.isIppatsu) return player
        return player.copy(playerRuleState = riichiState.copy(isIppatsu = false))
    }

    /**
     * 任何一次鳴牌都會讓場上所有玩家的一發資格失效。
     */
    override fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer> {
        return players.map { player ->
            val riichiState = player.playerRuleState as? RiichiPlayerState ?: return@map player
            if (!riichiState.isIppatsu) return@map player
            player.copy(playerRuleState = riichiState.copy(isIppatsu = false))
        }
    }

    /**
     * 檢查本次碰／明槓是否觸發大三元／大四喜的包牌責任，若觸發則寫入 [claimingPlayer] 的 [RiichiPlayerState]。
     */
    override fun applyPaoLiabilityIfTriggered(
        claimingPlayer: MahjongPlayer,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): MahjongPlayer {
        val riichiState = claimingPlayer.playerRuleState as? RiichiPlayerState ?: return claimingPlayer
        val liability = PaoDetector.check(claimingPlayer.hand, calledTile.tile, sourceDirection) ?: return claimingPlayer
        return claimingPlayer.copy(playerRuleState = riichiState.copy(paoLiability = liability))
    }

    /**
     * 計算日本麻將自摸的點數結算：透過 [RiichiHandValueContextCalculator] 與 [RiichiHandValueCalculator]
     * 算出役種、番符與 [RiichiPointResult]，再依莊閒身分或包牌責任換算成各玩家應付金額。
     *
     * @return 若 [player] 尚未摸牌，或計算結果並非自摸應有的點數結算形狀（理論上不會發生，僅作防呆），
     *         則回傳 null。
     */
    override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinSettlementResult? {
        val winningTile = player.hand.lastDrawn ?: return null

        // RiichiLegalActionValidator/RiichiHandDecomposer 的既有慣例是傳入的手牌「不含胡牌張」，
        // 胡牌張只透過下方 incomingTile 參數單獨傳入；player.hand.standingTiles 此時已經把
        // lastDrawn（即胡牌張）算進去了，這裡剝離避免重複計數。
        val playerForCalculation = player.copy(hand = player.hand.copy(lastDrawn = null))

        val context = createHandValueContextCalculator().calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = playerForCalculation,
                incomingTile = winningTile,
                isTsumo = true,
            ),
        )
        val result = createHandValueCalculator().calculate(context)

        val payments: Map<Uuid, Int> = when (val pointResult = result.pointResult) {
            is RiichiPointResult.DealerTsumo ->
                tableState.players
                    .filter { it.id != player.id }
                    .associate { it.id to pointResult.paymentPerNonDealer }

            is RiichiPointResult.NonDealerTsumo -> {
                val dealerId = tableState.players.first { it.currentWind == tableState.prevalentWind }.id
                tableState.players
                    .filter { it.id != player.id }
                    .associate { it.id to if (it.id == dealerId) pointResult.dealerPayment else pointResult.otherNonDealerPayment }
            }

            is RiichiPointResult.PaoTsumo -> {
                // 理論上不會發生：RiichiHandValueCalculator 只在 paoLiability 非 null 時才會回傳 PaoTsumo。
                val paoLiability = result.paoLiability ?: return null
                val paoPlayerId = tableState.players
                    .first { tableState.relativeDirectionOf(player.id, it.id) == paoLiability.direction }
                    .id
                mapOf(paoPlayerId to pointResult.total)
            }

            // Ron / PaoRon 理論上不會出現在 isTsumo = true 的計算結果中。若真的發生，視為此規則
            // 無法對這次自摸完成結算，回傳 null 讓呼叫端以 IllegalAction 處理，而非產生錯誤的結算。
            is RiichiPointResult.Ron, is RiichiPointResult.PaoRon -> return null
        }

        return WinSettlementResult(totalGained = result.totalPoint, paymentsByPlayerId = payments)
    }

    /**
     * 計算日本麻將榮和的點數結算：透過 [RiichiHandValueContextCalculator] 與 [RiichiHandValueCalculator]
     * 算出役種、番符與 [RiichiPointResult]，再依放銃者一人支付、或包牌責任成立時的分攤方式換算成
     * 各玩家應付金額。
     *
     * @return 若計算結果並非榮和應有的點數結算形狀（理論上不會發生，僅作防呆），則回傳 null。
     */
    override fun declareRon(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        discarderId: Uuid,
    ): WinSettlementResult? {
        // 榮和的胡牌張本來就不在贏家自己手上（是他家的捨牌），不像自摸的 lastDrawn 那樣有
        // 重複計數的疑慮，這裡不需要額外剝離手牌。
        val context = createHandValueContextCalculator().calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = winningTile,
                isTsumo = false,
            ),
        )
        val result = createHandValueCalculator().calculate(context)

        val payments: Map<Uuid, Int> = when (val pointResult = result.pointResult) {
            is RiichiPointResult.Ron -> mapOf(discarderId to pointResult.total)

            is RiichiPointResult.PaoRon -> {
                // 理論上不會發生：RiichiHandValueCalculator 只在 paoLiability 非 null 時才會回傳 PaoRon。
                val paoLiability = result.paoLiability ?: return null
                val paoPlayerId = tableState.players
                    .first { tableState.relativeDirectionOf(player.id, it.id) == paoLiability.direction }
                    .id
                // 包牌責任者剛好就是放銃者本人時，兩份「一半」其實是同一個人要付，直接歸戶成一筆
                // 全額，避免兩筆同 key 的付款在合併時互相覆蓋掉一半金額。
                if (paoPlayerId == discarderId) {
                    mapOf(discarderId to pointResult.total)
                } else {
                    mapOf(discarderId to pointResult.paymentEach, paoPlayerId to pointResult.paymentEach)
                }
            }

            // DealerTsumo/NonDealerTsumo/PaoTsumo 理論上不會出現在 isTsumo = false 的計算結果中。
            // 若真的發生，視為此規則無法對這次榮和完成結算，回傳 null 讓呼叫端以 IllegalAction 處理，
            // 而非產生錯誤的結算。
            is RiichiPointResult.DealerTsumo, is RiichiPointResult.NonDealerTsumo, is RiichiPointResult.PaoTsumo -> return null
        }

        return WinSettlementResult(totalGained = result.totalPoint, paymentsByPlayerId = payments)
    }

    /**
     * 收下場上所有立直棒：贏家獲得「立直棒數量 * 1000」點，收下後立直棒數量歸零。
     *
     * @return 若 [tableState] 的動態桌況狀態並非 [RiichiDynamicState]（理論上不會發生，僅作防呆），
     *         則回傳 null。
     */
    override fun collectStickPot(tableState: TableState): Pair<DynamicRuleState?, Int>? {
        val riichiDynamicState = tableState.dynamicRuleState as? RiichiDynamicState ?: return null
        return riichiDynamicState.copy(riichiStickCount = 0) to riichiDynamicState.riichiStickCount * 1000
    }
}
