package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.judgment.HandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RiichiDeclarationResult
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PlayerRuleState
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 台灣麻將規則模組實作。
 *
 * 負責串接台灣麻將特有的組件，包含 [TaiwanWallFactory] 與 [TaiwanDiscardPile]...等等。
 */
class TaiwanRuleModule(
    override val id: String,
    override val config: TaiwanRuleConfig,
) : MahjongRuleModule<TaiwanRuleConfig> {
    /**
     * 建立台灣麻將牌山工廠。
     *
     * @return [TaiwanWallFactory] 實體。
     */
    override fun createWallFactory(): TaiwanWallFactory = TaiwanWallFactory(config)

    /**
     * 建立台灣麻將專用的牌河。
     *
     * @return [TaiwanDiscardPile] 實體。
     */
    override fun createDiscardPile(): TaiwanDiscardPile = TaiwanDiscardPile()

    /**
     * 建立台灣麻將的向聽數計算器。
     *
     * @return [TaiwanShantenCalculator] 實體。
     */
    override fun createShantenCalculator(): TaiwanShantenCalculator = TaiwanShantenCalculator()

    /**
     * 建立台灣麻將的合法動作判定器。
     *
     * @return [TaiwanLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(): TaiwanLegalActionValidator = TaiwanLegalActionValidator()

    /**
     * 建立台灣麻將的手牌役種計算機。
     *
     * @return [HandValueCalculator] 實體。
     * @throws NotImplementedError 目前尚未實作 TaiwanHandValueCalculator。
     */
    override fun createHandValueCalculator(): HandValueCalculator<*, *> {
        TODO("TaiwanHandValueCalculator is not yet implemented")
    }

    /**
     * 建立台灣麻將的手牌役種上下文計算機。
     *
     * @return [HandValueContextCalculator] 實體。
     * @throws NotImplementedError 目前尚未實作 TaiwanHandValueContextCalculator。
     */
    override fun createHandValueContextCalculator(): HandValueContextCalculator<*, *> {
        TODO("TaiwanHandValueContextCalculator is not yet implemented")
    }

    /**
     * 台灣麻將目前沒有動態桌況狀態的需求。
     *
     * @return 固定回傳 null。
     */
    override fun createInitialDynamicState(): DynamicRuleState? = null

    /**
     * 台灣麻將目前沒有玩家規則狀態的需求。
     *
     * @return 固定回傳 null。
     */
    override fun createInitialPlayerRuleState(): PlayerRuleState? = null

    /**
     * 台灣麻將目前沒有立直宣告這個機制。
     *
     * @return 固定回傳 null。
     */
    override fun declareRiichi(
        tableState: TableState,
        player: MahjongPlayer,
        discardResult: Hand.DiscardResult,
    ): RiichiDeclarationResult? = null

    /**
     * 台灣麻將目前沒有摸牌後需要清除的規則特有狀態。
     *
     * @return 固定回傳 [player] 本身。
     */
    override fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer = player

    /**
     * 台灣麻將目前沒有鳴牌後需要清除的規則特有狀態。
     *
     * @return 固定回傳 [players] 本身。
     */
    override fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer> = players

    /**
     * 台灣麻將目前沒有包牌這個機制。
     *
     * @return 固定回傳 [claimingPlayer] 本身。
     */
    override fun applyPaoLiabilityIfTriggered(
        claimingPlayer: MahjongPlayer,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): MahjongPlayer = claimingPlayer

    /**
     * 台灣麻將目前沒有自摸結算的實作。
     *
     * @return 固定回傳 null。
     */
    override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinSettlementResult? = null

    /**
     * 台灣麻將目前沒有榮和結算的實作。
     *
     * @return 固定回傳 null。
     */
    override fun declareRon(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        discarderId: Uuid,
    ): WinSettlementResult? = null

    /**
     * 台灣麻將目前沒有立直/供託這個機制。
     *
     * @return 固定回傳 null。
     */
    override fun collectStickPot(tableState: TableState): Pair<DynamicRuleState?, Int>? = null

    /**
     * 台灣麻將目前沒有流局結算的實作。
     *
     * @return 固定回傳 null。
     */
    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult? = null

    /**
     * 台灣麻將目前沒有多家和判定為流局這個機制的具體流局原因型別。
     *
     * @return 固定回傳 null。
     */
    override fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason? = null
}
