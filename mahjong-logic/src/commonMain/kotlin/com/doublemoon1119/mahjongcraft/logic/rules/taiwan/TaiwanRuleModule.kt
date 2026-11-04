package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.PointPotCollection
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout.TaiwanWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening.TaiwanWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawDecision
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawReasonIds
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.drawFromReservedWall
import com.doublemoon1119.mahjongcraft.logic.util.isWind
import kotlin.uuid.Uuid

/**
 * 台灣麻將的規則與計算元件。
 *
 * @property id 規則模組的完整識別碼。
 * @property config 台灣麻將規則設定。
 */
class TaiwanRuleModule(
    override val id: String,
    override val config: TaiwanRuleConfig,
) : MahjongRuleModule<TaiwanRuleConfig> {
    /** 摸到花牌後自動公開並從保留區補牌。 */
    override fun createDrawFollowUpPolicy(): DrawFollowUpPolicy = TaiwanFlowerDrawPolicy

    /** 槓後從保留區補牌，並維持保留區張數。 */
    override fun createSupplementalDrawPolicy(): SupplementalDrawPolicy = SupplementalDrawPolicy { context ->
        if (context.action is GameAction.Kan || context.action == TAIWAN_REVEAL_FLOWER_ACTION) {
            drawFromReservedWall(context.tableStateAfterAction)
                ?: SupplementalDrawDecision.Rejected(SupplementalDrawReasonIds.WALL_EXHAUSTED)
        } else {
            SupplementalDrawDecision.NotRequired
        }
    }

    /** 台灣麻將手牌整理排序規則。 */
    override val tileOrder: TileOrder = TaiwanTileOrder

    /**
     * 建立台灣麻將牌山工廠。
     *
     * @return [TaiwanWallFactory] 實體。
     */
    override fun createWallFactory(playerCount: Int): TaiwanWallFactory = TaiwanWallFactory(config)

    /** 建立四人台灣麻將的三骰牌牆開門 policy。 */
    override fun createWallOpeningPolicy(): TaiwanWallOpeningPolicy = TaiwanWallOpeningPolicy

    /** 建立台灣麻將的牌牆布局，依 [TaiwanRuleConfig.useFlowerTiles] 支援 136 或 144 張。 */
    override fun createWallLayout(playerCount: Int): TaiwanWallLayout = TaiwanWallLayout(config)

    /**
     * 保留台灣麻將目前的流程行為：途中流局連莊，其他結算由莊家是否為得利者決定。
     *
     * @param context 本局結算後的完整結果情境。
     * @return 台灣麻將本局後的莊家推進決策。
     */
    override fun resolveRoundTransition(context: RoundTransitionContext): RoundTransitionDirective = when {
        context.classification == RoundCompletionClassification.ABORTIVE_DRAW -> RoundTransitionDirective.RepeatDealer
        context.tableState.dealerPlayerId in context.beneficiaryPlayerIds -> RoundTransitionDirective.RepeatDealer
        else -> RoundTransitionDirective.PassToNextSeat
    }

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

    /** 建立台灣麻將的台數計算器。 */
    override fun createHandValueCalculator(): TaiwanHandValueCalculator = TaiwanHandValueCalculator(config)

    /** 建立台灣麻將的計台上下文計算器。 */
    override fun createHandValueContextCalculator(): TaiwanHandValueContextCalculator = TaiwanHandValueContextCalculator()

    /** 從摸牌位或立牌中找出本次要公開的花牌。 */
    override fun tileSetAsideBy(player: MahjongPlayer, action: GameAction.Extension): IdentifiedTile? {
        if (action != TAIWAN_REVEAL_FLOWER_ACTION) return null
        return player.hand.lastDrawn?.takeIf { TaiwanTileTypes.isFlower(it.tile) }
            ?: player.hand.tiles.firstOrNull { TaiwanTileTypes.isFlower(it.tile) }
    }

    /** 將花牌移到公開花區並記錄補花動作。 */
    override fun applyTileSetAsideAction(
        tableState: TableState,
        actorPlayerId: Uuid,
        action: GameAction.Extension,
    ): TableState? {
        val player = tableState.players.firstOrNull { it.id == actorPlayerId } ?: return null
        val flower = tileSetAsideBy(player, action) ?: return null
        return TaiwanFlowerReplacement.reveal(tableState, actorPlayerId, flower)?.let { revealed ->
            revealed.copy(
                components = revealed.components.with(
                    TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP,
                    TaiwanPendingFlowerFollowUp(actorPlayerId, flower.id),
                ),
            )
        }
    }

    /**
     * 台灣麻將目前沒有自摸結算的實作。
     *
     * @return 固定回傳 null。
     */
    override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinResolutionResult? = null

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
        isRobbingKan: Boolean,
    ): WinResolutionResult? = null

    /**
     * 台灣麻將目前沒有立直/供託這個機制。
     *
     * @return 固定回傳 null。
     */
    override fun collectStickPot(tableState: TableState): PointPotCollection? = null

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

    /**
     * 判定第一巡四位玩家是否依序打出相同風牌，且期間沒有人鳴牌。
     *
     * @param tableStateAfterDiscard 捨牌反應結束後的桌況。
     * @return 選項已啟用且四風連打成立時的流局原因，否則為 null。
     */
    fun resolveFourWinds(tableStateAfterDiscard: TableState): ExhaustiveDrawReason? {
        if (!config.fourWindsAbortiveDraw) return null
        if (tableStateAfterDiscard.players.any { it.hand.exposedMelds.isNotEmpty() }) return null
        val firstDiscards = tableStateAfterDiscard.players.map { player ->
            player.discardPile.entries.singleOrNull()?.tile?.tile
        }
        if (firstDiscards.any { tile -> tile == null || !tile.isWind }) return null
        return TaiwanExhaustiveDrawReason.FourWinds.takeIf { firstDiscards.toSet().size == 1 }
    }

    /**
     * 判定全桌是否已合計完成四個槓；同一位玩家完成全部四槓也會成立。
     *
     * @param tableState 要統計各家槓子的桌況。
     * @return 全桌已有至少四個槓時的流局原因，否則為 null。
     */
    fun resolveFourKans(tableState: TableState): ExhaustiveDrawReason? {
        val totalKans = tableState.players.sumOf { player ->
            player.hand.exposedMelds.count { meld ->
                meld.type == MeldType.OPEN_KAN || meld.type == MeldType.ADDED_KAN || meld.type == MeldType.CLOSED_KAN
            }
        }
        return TaiwanExhaustiveDrawReason.FourKans.takeIf { totalKans >= 4 }
    }
}
