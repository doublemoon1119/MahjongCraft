package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.LegalActionAutomaticControlPolicy
import com.doublemoon1119.mahjongcraft.logic.module.MeldCompletedContext
import com.doublemoon1119.mahjongcraft.logic.module.PointPotCollection
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiPhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.opening.RiichiWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionClassification
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionDirective
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileComponentDescription
import com.doublemoon1119.mahjongcraft.logic.util.isWind
import kotlin.uuid.Uuid

/**
 * 日本麻將規則模組實作。
 *
 * 串接四人立直的牌山、牌河、動作判定、結算與賽程政策。
 *
 * @property id 規則模組的命名空間識別碼。
 * @property config 四人立直規則設定。
 */
class RiichiRuleModule(
    override val id: String,
    override val config: RiichiRuleConfig,
) : RiichiFamilyRuleModule<RiichiRuleConfig> {
    /** 四人日本麻將支援的本局自動操作控制。 */
    override fun getSupportedAutomaticControlIds(): Set<String> = setOf(
        BuiltInAutomaticControlIds.AUTO_WIN,
        BuiltInAutomaticControlIds.DECLINE_CALLS,
        BuiltInAutomaticControlIds.AUTO_TSUMOGIRI,
    )

    /** 本局自動操作只依合法動作決定，與其他規則共用同一套判斷。 */
    override fun createAutomaticControlPolicy(): LegalActionAutomaticControlPolicy = LegalActionAutomaticControlPolicy

    /** 日麻只公開已經能從桌面立直棒觀察到的立直狀態。 */
    override fun getPublicPlayerIndicators(tableState: TableState, player: MahjongPlayer): List<PublicPlayerIndicator> = if (isPlayerInRiichi(player)) listOf(PublicPlayerIndicator(RiichiPublicIndicatorIds.RIICHI)) else emptyList()

    /** 日本麻將手牌整理排序規則。 */
    override val tileOrder: TileOrder = RiichiTileOrder

    /** 建立支援南入、西入、驟死、和了止め與擊飛的日麻 progression policy。 */
    override fun createMatchProgressionPolicy(): MatchProgressionPolicy = RiichiMatchProgressionPolicy(config)

    /**
     * 莊家和牌、荒牌流局時莊家聽牌，或發生途中流局時，均由莊家連莊；其餘情況過莊。
     *
     * @param context 本局結算後的完整結果情境。
     * @return 日本麻將本局後的莊家推進決策。
     */
    override fun resolveRoundTransition(context: RoundTransitionContext): RoundTransitionDirective = when {
        context.classification == RoundCompletionClassification.ABORTIVE_DRAW -> RoundTransitionDirective.RepeatDealer
        context.tableState.dealerPlayerId in context.beneficiaryPlayerIds -> RoundTransitionDirective.RepeatDealer
        else -> RoundTransitionDirective.PassToNextSeat
    }

    /**
     * 建立日本麻將牌山工廠。
     *
     * @return [RiichiWallFactory] 實體。
     */
    override fun createWallFactory(playerCount: Int): RiichiWallFactory = RiichiWallFactory(config)

    /** 建立四人日本麻將的雙骰牌牆開門 policy。 */
    override fun createWallOpeningPolicy(): RiichiWallOpeningPolicy = RiichiWallOpeningPolicy

    /** 建立四人日本麻將固定 136 張的牌牆布局。 */
    override fun createWallLayout(playerCount: Int): RiichiWallLayout = RiichiWallLayout(config)

    /** 建立日本麻將獨立王牌區與槓後補位所使用的抽象實體布局 policy。 */
    override fun createPhysicalWallLayoutPolicy(): PhysicalWallLayoutPolicy = RiichiPhysicalWallLayoutPolicy(rinshanTileCount = config.rinshanTileCount)

    /** 建立將日麻赤五解讀為普通五的牌面 policy。 */
    override fun createTileInterpretationPolicy(): TileInterpretationPolicy = RiichiTileInterpretationPolicy

    /** 建立日本麻將槓後嶺上補牌與死牌區變化 policy。 */
    override fun createSupplementalDrawPolicy(): SupplementalDrawPolicy = RiichiSupplementalDrawPolicy

    /** 建立日麻槓寶牌公開時序 policy。 */
    override fun createWallRevealPolicy(): WallRevealPolicy = RiichiWallRevealPolicy

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
     * 建立日本麻將的手牌分析器。
     *
     * @return [RiichiDiscardReadinessAnalyzer] 實體。
     */
    override fun createDiscardReadinessAnalyzer(): RiichiDiscardReadinessAnalyzer = RiichiDiscardReadinessAnalyzer(
        shantenCalculator = createShantenCalculator(),
        legalActionValidator = createLegalActionValidator(),
    )

    /** 建立與正式結算共用役種、點數與起胡判定的日麻規則查詢。 */
    override fun createPositionRules(): RiichiPositionRules = RiichiPositionRules(
        config = config,
        handValueCalculator = createHandValueCalculator(),
        shantenCalculator = createShantenCalculator(),
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

    /** 目前正式公開的寶牌指示牌可被所有合法讀取者辨識，並正面朝上擺放。 */
    override fun describeTileComponents(tableState: TableState): List<TileComponentDescription> = riichiTileComponentDescriptions(tableState)

    /**
     * 若玩家已立直且仍有一發資格，這次捨牌代表一發的期限已經結束（立直後一巡內未能胡牌），故清除一發資格。
     */
    override fun onPlayerDiscarded(player: MahjongPlayer): ComponentPatch = player.riichiIppatsuEndPatch()

    /**
     * 任何一次鳴牌都會讓場上所有玩家的一發資格失效。
     */
    override fun onMeldCompleted(context: MeldCompletedContext): Map<Uuid, ComponentPatch> = context.playersAfter.associate { it.id to it.riichiIppatsuEndPatch() }

    /**
     * 檢查本次碰／明槓是否觸發大三元／大四喜的包牌責任，若觸發則寫入 [claimingPlayer] 的包牌組件。
     *
     * 吃不構成包牌，不做任何變更。
     */
    override fun beforeDiscardClaimed(
        claimingPlayer: MahjongPlayer,
        meldType: MeldType,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): ComponentPatch = claimingPlayer.riichiPaoLiabilityPatch(meldType, calledTile, sourceDirection)

    /**
     * 計算日本麻將自摸的點數結算：透過 [RiichiHandValueContextCalculator] 與 [RiichiHandValueCalculator]
     * 算出役種、番符與 [RiichiPointResult]，再依莊閒身分或包牌責任換算成各玩家應付金額。
     *
     * @return 若 [player] 尚未摸牌，或計算結果並非自摸應有的點數結算形狀（理論上不會發生，僅作防呆），
     *         則回傳 null。
     */
    override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinResolutionResult? {
        val winningTile = player.hand.lastDrawn ?: return null

        // RiichiLegalActionValidator/RiichiHandDecomposer 的既有慣例是傳入的手牌「不含胡牌張」，
        // 胡牌張只透過下方 incomingTile 參數單獨傳入；player.hand.standingTiles 此時已經把
        // lastDrawn（即胡牌張）算進去了，這裡剝離避免重複計數。
        val playerForCalculation = player.copy(hand = player.hand.copy(lastDrawn = null))

        val context = createHandValueContextCalculator().calculate(
            input = RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = playerForCalculation,
                incomingTile = winningTile,
                isTsumo = true,
            ),
        )
        val result = createHandValueCalculator().calculate(context)
        return riichiTsumoResolution(tableState, player.id, result, isTsumoLoss = false)
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
        isRobbingKan: Boolean,
    ): WinResolutionResult? {
        // 榮和的胡牌張本來就不在贏家自己手上（是他家的捨牌），不像自摸的 lastDrawn 那樣有
        // 重複計數的疑慮，這裡不需要額外剝離手牌。
        val context = createHandValueContextCalculator().calculate(
            input = RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = winningTile,
                isTsumo = false,
                isRobbingKan = isRobbingKan,
            ),
        )
        val result = createHandValueCalculator().calculate(context)
        return riichiRonResolution(tableState, player.id, discarderId, result)
    }

    /** 收下供託點數，只將供託組件歸零。 */
    override fun collectStickPot(tableState: TableState): PointPotCollection = collectRiichiPot(tableState)

    /**
     * 依 [riichiComboBonusPayments] 分攤本場點數；[resolution] 成立包牌時由包牌責任者承擔對應部分。
     */
    override fun resolveComboBonusPayments(
        tableState: TableState,
        winnerId: Uuid,
        discarderId: Uuid?,
        resolution: WinResolutionResult?,
    ): Map<Uuid, Int> {
        val paoLiability = (resolution?.handValueResult as? RiichiHandValueResult)?.paoLiability
        return riichiComboBonusPayments(
            tableState = tableState,
            winnerId = winnerId,
            discarderId = discarderId,
            paoPlayerId = paoLiability?.let { tableState.riichiPaoPlayerId(winnerId, it) },
        )
    }

    /**
     * 計算一般流局（牌山摸盡）的點數結算：聽牌判定（[ShantenResult] 非 [ShantenResult.NotTenpai]
     * 皆視為聽牌，`Complete` 理論上不會在流局判定時出現，僅作寬鬆處理），並依
     * [riichiExhaustiveDrawSettlement] 計算不聽罰符。流局滿貫已由 [resolveNagashiMangan] 在普通流局之前判定。
     *
     * 只計入 [TableState.activePlayers]：本局已經胡牌退場的玩家（見 [TableState.finishedPlayerIds]）
     * 手牌早已收起、也不再摸打，既不該被判聽牌／不聽，也不該收付不聽罰符。沒有玩家中途退場時，
     * [TableState.activePlayers] 就是全體玩家。
     */
    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult = declareExhaustiveDraw(tableState, emptySet())

    override fun declareExhaustiveDraw(
        tableState: TableState,
        notenDeclaredPlayerIds: Set<Uuid>,
    ): ExhaustiveDrawSettlementResult = riichiExhaustiveDrawSettlement(
        tableState = tableState,
        shantenCalculator = createShantenCalculator(),
        notenPenaltyUnit = config.scoreConfig.notenPenaltyUnit,
        notenDeclaredPlayerIds = notenDeclaredPlayerIds,
    )

    /**
     * 判定流局滿貫並計算自摸滿貫式收支；沒有任何人成立時回傳 `null`。
     *
     * 成立條件為牌河非空、全部是么九牌且從未被鳴走。此函式不修改桌況，也不收取供託。
     *
     * 只計入 [TableState.activePlayers]：本局已胡牌退場的玩家不再參與流局計算，其牌河也不該成立
     * 流局滿貫。
     */
    override fun resolveNagashiMangan(tableState: TableState): RiichiNagashiManganResolution? = riichiNagashiMangan(tableState)

    /** 九種九牌需要公開宣告者手牌作為成立證明，其餘途中流局不公開手牌。 */
    override fun resolveAbortiveDrawRevealedHands(
        tableState: TableState,
        declarerId: Uuid?,
        reason: ExhaustiveDrawReason,
    ): List<RevealedHandSettlement> = riichiAbortiveDrawRevealedHands(tableState, declarerId, reason)

    /**
     * 多家和判定為流局時，日本麻將對應的具體流局原因固定為三家和了。
     */
    override fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason = RiichiExhaustiveDrawReason.SanchaHou

    /**
     * 四風連打：日麻慣稱的途中流局——第一巡、四名玩家的第一張捨牌皆為同一種風牌、且無人鳴牌反應。
     * 全場尚未有人鳴牌，且全員恰好都打過一張牌（`entries.singleOrNull()` 只有在這個情境下才會
     * 全員非 null），這些第一張捨牌若皆為同一種風牌則成立。
     *
     * 呼叫端須先處理捨牌反應，確認無鳴牌或和牌結果後再判定；此方法本身不檢查反應狀態。
     *
     * @param tableStateAfterDiscard 捨牌後的桌況。
     * @return 首次捨牌符合四風連打條件時的原因，否則為 null。
     */
    fun resolveSuufonRenda(tableStateAfterDiscard: TableState): ExhaustiveDrawReason? {
        if (tableStateAfterDiscard.players.any { it.hand.exposedMelds.isNotEmpty() }) return null
        val firstDiscards = tableStateAfterDiscard.players.map { it.discardPile.entries.singleOrNull()?.tile?.tile }
        if (firstDiscards.any { it == null || !it.isWind }) return null
        return if (firstDiscards.toSet().size == 1) RiichiExhaustiveDrawReason.SuufonRenda else null
    }

    /**
     * 四家立直：全員皆已宣告立直則成立。呼叫端只在剛套用完一次立直宣告、且確定沒人反應時才會
     * 呼叫這個方法，所以「全員皆立直」這個條件只可能在恰好完成的那次宣告變成 true
     * （玩家只能宣告立直一次，見 [RiichiLegalActionValidator] 的 `!isRiichi` 合法性檢查）。日麻限定，不屬於
     * 通用規則契約。
     *
     * @param tableStateAfterDeclaration 立直宣告後的桌況。
     * @return 所有玩家都已立直時的四家立直原因，否則為 null。
     */
    fun resolveSuuchaRiichi(tableStateAfterDeclaration: TableState): ExhaustiveDrawReason? {
        val allRiichi = tableStateAfterDeclaration.players.all { it.riichiDeclaration != null }
        return if (allRiichi) RiichiExhaustiveDrawReason.SuuchaRiichi else null
    }

    /**
     * 四槓散了：逐玩家算出各自的槓子數（明槓/暗槓/加槓皆算）加總得到全場總數，未滿 4 個不成立；
     * 達到 4 個（含）以上時，若其中有一位玩家的槓子數就等於全場總數，代表全部槓子都是他一人
     * 達成（該玩家可能正在做四槓子役滿），此時不成立。
     */
    override fun resolveSuukanNagare(tableState: TableState): ExhaustiveDrawReason? = riichiSuukanNagare(tableState)

    /** 加值牌：帶赤寶牌特徵的牌，或任一生效寶牌指示牌的下一張。 */
    override fun isBonusTile(face: TileFaceView, wallFaces: List<TileFaceView>): Boolean = riichiIsBonusTile(face, wallFaces, usesThreePlayerTiles = false)

    /**
     * 判定玩家是否已宣告立直或雙立直。
     *
     * @param player 待判定玩家。
     * @return 具有立直狀態時為 true；不是立直玩家狀態時為 false。
     */
    fun isPlayerInRiichi(player: MahjongPlayer): Boolean = player.riichiDeclaration != null

    /**
     * 立直後只能摸切，因此回傳玩家剛摸入的牌；未立直或尚未摸牌時不額外限制。
     */
    override fun discardableTileIds(tableState: TableState, player: MahjongPlayer): Set<Uuid>? = player.hand.lastDrawn?.id?.takeIf { isPlayerInRiichi(player) }?.let(::setOf)

    /**
     * 讀取尚未被收下的立直棒數，不修改供託。
     *
     * @param tableState 目前桌況。
     * @return 立直棒數。
     * @throws IllegalArgumentException 桌況缺少日麻供託組件時。
     */
    fun getStickPotCount(tableState: TableState): Int = tableState.riichiPotPoints() / RIICHI_STICK_POINTS

    /** 立直中的玩家放過和牌時記錄本局永久振聽；未立直時放過和牌只構成同巡振聽，不做任何變更。 */
    override fun onPlayerDeclinedWin(player: MahjongPlayer): ComponentPatch = player.riichiPermanentFuritenPatch()
}
