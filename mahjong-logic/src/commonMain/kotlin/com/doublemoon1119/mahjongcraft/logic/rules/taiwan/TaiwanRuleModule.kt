package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardEffect
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationDiscardPolicy
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.PointPotCollection
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicatorValue
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.layout.TaiwanWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.opening.TaiwanWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.DrawFollowUpPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionPolicy
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

    /** 打完選定圈數後結束，末局莊家連莊時打到下莊為止。 */
    override fun createMatchProgressionPolicy(): MatchProgressionPolicy = TaiwanMatchProgressionPolicy(config.scheduledRoundCount)

    /**
     * 荒牌流局與途中流局都由莊家連莊；其他結果由莊家是否為得利者決定。
     *
     * @param context 本局結算後的完整結果情境。
     * @return 台灣麻將本局後的莊家推進決策。
     */
    override fun resolveRoundTransition(context: RoundTransitionContext): RoundTransitionDirective = when {
        context.classification == RoundCompletionClassification.EXHAUSTIVE_DRAW -> RoundTransitionDirective.RepeatDealer
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

    /** 建立台灣麻將的捨牌與目前手牌聽牌分析器。 */
    override fun createDiscardReadinessAnalyzer(): TaiwanDiscardReadinessAnalyzer = TaiwanDiscardReadinessAnalyzer(
        config = config,
        declarationDiscardPolicy = declarationDiscardPolicy(),
        onPlayerDiscarded = ::onPlayerDiscarded,
    )

    /**
     * 建立台灣麻將的合法動作判定器。
     *
     * @return [TaiwanLegalActionValidator] 實體。
     */
    override fun createLegalActionValidator(): TaiwanLegalActionValidator = TaiwanLegalActionValidator(config)

    /** 宣告聽牌後只能打出剛摸入的牌；吃碰後依食替限制。 */
    override fun discardableTileIds(tableState: TableState, player: MahjongPlayer): Set<Uuid>? = TaiwanDiscardRestriction.discardableTileIds(player)

    /**
     * 宣告聽牌並打牌：打出後必須仍聽牌。莊家第一打為天聽；全桌沒有副露時，閒家第一打或莊家第二打為地聽；其他時候為一般宣告。
     */
    override fun declarationDiscardPolicy(): DeclarationDiscardPolicy = DeclarationDiscardPolicy { tableState, player, action, discardResult ->
        if (action != TAIWAN_DECLARE_READY_ACTION || player.components[TaiwanPlayerComponents.READY_DECLARATION] != null) return@DeclarationDiscardPolicy null
        if (createLegalActionValidator().waits(discardResult.hand).isEmpty()) return@DeclarationDiscardPolicy null
        val noMelds = tableState.players.all { it.hand.melds.isEmpty() }
        val isDealer = player.id == tableState.dealerPlayerId
        val previousDiscards = player.discardPile.entries.size
        val kind = when {
            noMelds && isDealer && previousDiscards == 0 -> TaiwanReadyKind.HEAVENLY
            noMelds && previousDiscards == (if (isDealer) 1 else 0) -> TaiwanReadyKind.EARTHLY
            else -> TaiwanReadyKind.GENERAL
        }
        DeclarationDiscardEffect(
            playerComponents = ComponentPatch.basedOn(player.components, values = ComponentSet.EMPTY.with(TaiwanPlayerComponents.READY_DECLARATION, kind)),
            discardComponents = ComponentSet.EMPTY,
            tableComponents = ComponentPatch(),
        )
    }

    /** 放過胡牌後進入過水；已宣告天聽或地聽時改為一般宣告聽牌。 */
    override fun onPlayerDeclinedWin(player: MahjongPlayer): ComponentPatch {
        val declaration = player.components[TaiwanPlayerComponents.READY_DECLARATION]
        val values = ComponentSet.EMPTY.with(TaiwanPlayerComponents.PASSED_WIN, Unit).let { values ->
            if (declaration == TaiwanReadyKind.HEAVENLY || declaration == TaiwanReadyKind.EARTHLY) {
                values.with(TaiwanPlayerComponents.READY_DECLARATION, TaiwanReadyKind.GENERAL)
            } else {
                values
            }
        }
        return ComponentPatch.basedOn(player.components, values = values)
    }

    /** 過水期間打出一張自己不能胡的牌即解除。 */
    override fun onPlayerDiscarded(player: MahjongPlayer): ComponentPatch {
        if (player.components[TaiwanPlayerComponents.PASSED_WIN] == null) return ComponentPatch()
        val discarded = player.discardPile.entries.lastOrNull()?.tile?.tile ?: return ComponentPatch()
        if (discarded in createLegalActionValidator().waits(player.hand)) return ComponentPatch()
        return ComponentPatch.basedOn(player.components, removals = setOf(TaiwanPlayerComponents.PASSED_WIN))
    }

    /** 宣告聽牌的種類，以及連莊一次以上的莊家的連莊次數。 */
    override fun getPublicPlayerIndicators(tableState: TableState, player: MahjongPlayer): List<PublicPlayerIndicator> = listOfNotNull(
        player.components[TaiwanPlayerComponents.READY_DECLARATION]?.let { PublicPlayerIndicator(it.indicatorId) },
        PublicPlayerIndicator(TaiwanPublicIndicatorIds.DEALER_STREAK, PublicPlayerIndicatorValue.Count(tableState.comboCount))
            .takeIf { player.id == tableState.dealerPlayerId && tableState.comboCount > 0 },
    )

    /** 建立與正式結算共用計台與付款計算的台灣麻將規則查詢。 */
    override fun createPositionRules(): TaiwanPositionRules = TaiwanPositionRules(config)

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

    /** 將花牌移到公開花區並記錄補花動作；公開證據由共用的移出流程記錄。 */
    override fun applyTileSetAsideAction(
        tableState: TableState,
        actorPlayerId: Uuid,
        action: GameAction.Extension,
    ): TableState? {
        val player = tableState.players.firstOrNull { it.id == actorPlayerId } ?: return null
        val flower = tileSetAsideBy(player, action) ?: return null
        return TaiwanFlowerReplacement.reveal(tableState, actorPlayerId, flower)?.let { revealed ->
            revealed.copy(
                publicTileEvidenceIds = tableState.publicTileEvidenceIds,
                components = revealed.components.with(
                    TaiwanTableComponents.PENDING_FLOWER_FOLLOW_UP,
                    TaiwanPendingFlowerFollowUp(actorPlayerId, flower.id, replacementsDue = 1),
                ),
            )
        }
    }

    /** 自摸由其他三家各付一筆；手牌不成立或未達最低台數時為 null。 */
    override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinResolutionResult? {
        val winningTile = player.hand.lastDrawn ?: return null
        return resolveWin(tableState, player.copy(hand = player.hand.copy(lastDrawn = null)), winningTile, discarderId = null, isRobbingKong = false)
    }

    /** 放槍與搶槓由責任者付一筆；手牌不成立或未達最低台數時為 null。 */
    override fun declareRon(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        discarderId: Uuid,
        isRobbingKan: Boolean,
    ): WinResolutionResult? = resolveWin(tableState, player, winningTile, discarderId, isRobbingKan)

    /**
     * 計台並計算付款。
     *
     * @param tableState 胡牌前的桌況。
     * @param player 手牌不含胡牌張的玩家。
     * @param winningTile 胡牌張。
     * @param discarderId 單獨付款者；自摸為 null。
     * @param isRobbingKong 是否搶加槓。
     * @return 結算結果；不成立時為 null。
     */
    private fun resolveWin(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        discarderId: Uuid?,
        isRobbingKong: Boolean,
    ): WinResolutionResult? {
        val handValue = TaiwanWinEvaluation(config).evaluate(tableState, player, winningTile, isTsumo = discarderId == null, isRobbingKong = isRobbingKong)
        if (!handValue.isWinningHand) return null
        val settlement = TaiwanPaymentCalculator.calculate(
            config = config.scoreConfig,
            playerIds = tableState.players.map { it.id },
            winnerId = player.id,
            dealerId = tableState.dealerPlayerId,
            handTai = handValue.totalTai,
            dealerStreak = tableState.comboCount,
            discarderId = discarderId,
        )
        return WinResolutionResult(settlement, handValue)
    }

    /**
     * 台灣麻將目前沒有立直/供託這個機制。
     *
     * @return 固定回傳 null。
     */
    override fun collectStickPot(tableState: TableState): PointPotCollection? = null

    /** 荒牌流局不付款；牌牆尚未摸完時為 null。 */
    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult? {
        if (tableState.tileWall.remainingCount > 0) return null
        return ExhaustiveDrawSettlementResult(
            reason = TaiwanExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = emptySet(),
            stickPotCollectorPlayerIds = emptySet(),
            scoreDeltas = tableState.players.associate { it.id to 0 },
        )
    }

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
                meld.type == MeldType.OpenKan || meld.type == MeldType.AddedKan || meld.type == MeldType.ClosedKan
            }
        }
        return TaiwanExhaustiveDrawReason.FourKans.takeIf { totalKans >= 4 }
    }
}
