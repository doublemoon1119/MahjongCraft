package com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.NagashiManganResolution
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PULL_NORTH_GAME_ACTION
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.PaoDetector
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RIICHI_STICK_POINTS
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiAutomaticControlPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardReadinessAnalyzer
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiLegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiMatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerState
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPositionRules
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiShantenCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiSupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTileOrder
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiWallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiPhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiAbortiveDrawRevealedHands
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiComboBonusPayments
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiExhaustiveDrawSettlement
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiNagashiMangan
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiPaoPlayerId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiRonResolution
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiSuukanNagare
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiTsumoResolution
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora.getNextDora
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.MatchProgressionPolicy
import com.doublemoon1119.mahjongcraft.logic.table.SupplementalDrawPolicy
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.WallRevealPolicy
import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import kotlin.uuid.Uuid

/**
 * 三人日本麻將規則模組。
 *
 * 與四人日麻共用役種、點數、立直與牌河等元件，差別在牌組（沒有二～八萬）、三面牌牆、不能吃、可以拔北、
 * 自摸損，以及只有九種九牌與四槓散了兩種途中流局。
 */
class ThreePlayerRiichiRuleModule(
    override val id: String,
    override val config: ThreePlayerRiichiRuleConfig,
) : RiichiFamilyRuleModule<ThreePlayerRiichiRuleConfig> {
    /** 與四人日麻相同的本局自動操作控制。 */
    override fun getSupportedAutomaticControlIds(): Set<String> = setOf(
        BuiltInAutomaticControlIds.AUTO_WIN,
        BuiltInAutomaticControlIds.DECLINE_CALLS,
        BuiltInAutomaticControlIds.AUTO_TSUMOGIRI,
    )

    /** 與四人日麻相同的本局自動操作 policy。 */
    override fun createAutomaticControlPolicy(): RiichiAutomaticControlPolicy = RiichiAutomaticControlPolicy

    /** 只公開已經能從桌面立直棒觀察到的立直狀態。 */
    override fun getPublicPlayerIndicators(tableState: TableState, player: MahjongPlayer): List<PublicPlayerIndicator> = if (isPlayerInRiichi(player)) listOf(PublicPlayerIndicator(RiichiRuleModule.RIICHI_INDICATOR_ID)) else emptyList()

    /** 與四人日麻相同的手牌整理排序。 */
    override val tileOrder: TileOrder = RiichiTileOrder

    /** 每圈三局的日麻 progression policy。 */
    override fun createMatchProgressionPolicy(): MatchProgressionPolicy = RiichiMatchProgressionPolicy(config)

    /** 建立 108 張的三人麻將牌山。 */
    override fun createWallFactory(): ThreePlayerRiichiWallFactory = ThreePlayerRiichiWallFactory(config)

    /** 建立在三面牌牆之間數骰子的開門 policy。 */
    override fun createWallOpeningPolicy(): ThreePlayerRiichiWallOpeningPolicy = ThreePlayerRiichiWallOpeningPolicy

    /** 建立三面、每面 18 墩的牌牆布局。 */
    override fun createWallLayout(): ThreePlayerRiichiWallLayout = ThreePlayerRiichiWallLayout(config)

    /** 與四人日麻共用的王牌區布局；嶺上牌為 8 張。 */
    override fun createPhysicalWallLayoutPolicy(): PhysicalWallLayoutPolicy = RiichiPhysicalWallLayoutPolicy(rinshanTileCount = config.rinshanTileCount)

    /** 將赤五解讀為普通五的牌面 policy。 */
    override fun createTileInterpretationPolicy(): TileInterpretationPolicy = RiichiTileInterpretationPolicy

    /** 槓與拔北後的嶺上補牌 policy。 */
    override fun createSupplementalDrawPolicy(): SupplementalDrawPolicy = RiichiSupplementalDrawPolicy

    /** 槓寶牌公開時序 policy；拔北不公開槓寶牌。 */
    override fun createWallRevealPolicy(): WallRevealPolicy = RiichiWallRevealPolicy

    /** 與四人日麻相同的牌河。 */
    override fun createDiscardPile(): RiichiDiscardPile = RiichiDiscardPile()

    /** 與四人日麻相同的向聽數計算。 */
    override fun createShantenCalculator(): RiichiShantenCalculator = RiichiShantenCalculator()

    /** 不能吃、可以拔北的合法動作判定。 */
    override fun createLegalActionValidator(): RiichiLegalActionValidator = RiichiLegalActionValidator(
        shantenCalculator = createShantenCalculator(),
        handValueCalculator = createHandValueCalculator(),
        contextCalculator = createHandValueContextCalculator(),
        allowsChi = false,
        allowsPullNorth = true,
    )

    /** 建立手牌分析器。 */
    override fun createDiscardReadinessAnalyzer(): RiichiDiscardReadinessAnalyzer = RiichiDiscardReadinessAnalyzer(
        shantenCalculator = createShantenCalculator(),
        legalActionValidator = createLegalActionValidator(),
    )

    /** 建立與正式結算共用役種、點數與起胡判定的規則查詢。 */
    override fun createPositionRules(): RiichiPositionRules = RiichiPositionRules(
        config = config,
        handValueCalculator = createHandValueCalculator(),
        shantenCalculator = createShantenCalculator(),
    )

    /** 與四人日麻相同的手牌價值計算；拔北寶牌由上下文帶入。 */
    override fun createHandValueCalculator(): RiichiHandValueCalculator = RiichiHandValueCalculator(useLocalYaku = config.useLocalYaku)

    /** 帶入三人麻將牌組與拔北寶牌的手牌價值上下文計算。 */
    override fun createHandValueContextCalculator(): RiichiHandValueContextCalculator = RiichiHandValueContextCalculator(config)

    /** 全新的動態桌況狀態。 */
    override fun createInitialDynamicState(): RiichiDynamicState = RiichiDynamicState()

    /** 只延續尚未被收下的立直棒；槓、拔北與槓寶牌公開進度都只屬於單局。 */
    override fun createNextRoundDynamicState(previous: DynamicRuleState?): RiichiDynamicState = RiichiDynamicState(riichiStickCount = (previous as? RiichiDynamicState)?.riichiStickCount ?: 0)

    /** 全新的玩家規則狀態（尚未立直、沒有拔北）。 */
    override fun createInitialPlayerRuleState(): RiichiPlayerState = RiichiPlayerState()

    /** 摸牌不改變規則狀態。 */
    override fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer = player

    /** 立直後的下一次捨牌結束一發期限。 */
    override fun onPlayerDiscarded(player: MahjongPlayer): MahjongPlayer = player.withoutIppatsu()

    /** 任何一次鳴牌都會讓場上所有玩家的一發資格失效。 */
    override fun onMeldClaimed(players: List<MahjongPlayer>): List<MahjongPlayer> = players.map { it.withoutIppatsu() }

    /** 碰／明槓觸發大三元或大四喜時記錄包牌責任；三人麻將不能吃。 */
    override fun beforeDiscardClaimed(
        claimingPlayer: MahjongPlayer,
        meldType: MeldType,
        calledTile: IdentifiedTile,
        sourceDirection: RelativeDirection,
    ): MahjongPlayer {
        if (meldType != MeldType.PON && meldType != MeldType.OPEN_KAN) return claimingPlayer
        val riichiState = claimingPlayer.playerRuleState as? RiichiPlayerState ?: return claimingPlayer
        val liability = PaoDetector.check(claimingPlayer.hand, calledTile.tile, sourceDirection) ?: return claimingPlayer
        return claimingPlayer.copy(playerRuleState = riichiState.copy(paoLiability = liability))
    }

    /** 自摸結算；少一家支付，贏家收到的點數是兩家付款的總和（自摸損）。 */
    override fun declareTsumo(tableState: TableState, player: MahjongPlayer): WinResolutionResult? {
        val winningTile = player.hand.lastDrawn ?: return null
        // 計算時的手牌不含和牌張，和牌張另以 incomingTile 傳入。
        val playerForCalculation = player.copy(hand = player.hand.copy(lastDrawn = null))
        val context = createHandValueContextCalculator().calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = playerForCalculation,
                incomingTile = winningTile,
                isTsumo = true,
            ),
        )
        return riichiTsumoResolution(tableState, player.id, createHandValueCalculator().calculate(context), isTsumoLoss = true)
    }

    /**
     * 榮和結算；榮和拔出的北時 [discarderId] 是拔北的玩家，不計搶槓。
     */
    override fun declareRon(
        tableState: TableState,
        player: MahjongPlayer,
        winningTile: IdentifiedTile,
        discarderId: Uuid,
        isRobbingKan: Boolean,
    ): WinResolutionResult? {
        val context = createHandValueContextCalculator().calculate(
            RiichiHandValueContextCalculator.Input(
                tableState = tableState,
                player = player,
                incomingTile = winningTile,
                isTsumo = false,
                isRobbingKan = isRobbingKan,
            ),
        )
        return riichiRonResolution(tableState, player.id, discarderId, createHandValueCalculator().calculate(context))
    }

    /** 收下場上所有立直棒。 */
    override fun collectStickPot(tableState: TableState): Pair<DynamicRuleState?, Int>? {
        val riichiDynamicState = tableState.dynamicRuleState as? RiichiDynamicState ?: return null
        return riichiDynamicState.copy(riichiStickCount = 0) to riichiDynamicState.riichiStickCount * RIICHI_STICK_POINTS
    }

    /** 每本場 200 點：榮和由放銃者支付，自摸由另外兩家各付 100。 */
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

    /** 一般流局；不聽罰符總額 2000 點。 */
    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult = riichiExhaustiveDrawSettlement(tableState, createShantenCalculator(), config.scoreConfig.notenPenaltyUnit)

    /** 流局滿貫；付款方式與自摸相同，少一家支付。 */
    override fun resolveNagashiMangan(tableState: TableState): NagashiManganResolution? = riichiNagashiMangan(tableState)

    /** 九種九牌需要公開宣告者手牌作為成立證明，其餘途中流局不公開手牌。 */
    override fun resolveAbortiveDrawRevealedHands(
        tableState: TableState,
        declarerId: Uuid?,
        reason: ExhaustiveDrawReason,
    ): List<RevealedHandSettlement> = riichiAbortiveDrawRevealedHands(tableState, declarerId, reason)

    /** 三人對局沒有三家和；多家和流局設定下沿用三家和了作為原因。 */
    override fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason = RiichiExhaustiveDrawReason.SanchaHou

    /** 四槓散了。 */
    override fun resolveSuukanNagare(tableState: TableState): ExhaustiveDrawReason? = riichiSuukanNagare(tableState)

    /** 加值牌：赤寶牌，或依三人麻將牌組符合任一寶牌指示牌下一張的牌。 */
    override fun isBonusTile(tile: Tile, revealedWallTiles: List<Tile>): Boolean = RiichiTileInterpretationPolicy.isRedDora(tile) ||
        revealedWallTiles.any { indicator -> tile.riichiCanonical == getNextDora(indicator, usesThreePlayerTiles = true).riichiCanonical }

    /** 這位玩家目前是否立直中。 */
    fun isPlayerInRiichi(player: MahjongPlayer): Boolean = (player.playerRuleState as? RiichiPlayerState)?.isRiichi ?: false

    /** 立直後只能摸切。 */
    override fun forcedDiscardTileId(tableState: TableState, player: MahjongPlayer): Uuid? = player.hand.lastDrawn?.id?.takeIf { isPlayerInRiichi(player) }

    /** 場上尚未被收下的立直棒支數。 */
    fun getStickPotCount(tableState: TableState): Int = (tableState.dynamicRuleState as? RiichiDynamicState)?.riichiStickCount ?: 0

    /** 立直中放過和牌會成為永久振聽。 */
    override fun onPlayerDeclinedWin(player: MahjongPlayer): MahjongPlayer {
        val riichiState = player.playerRuleState as? RiichiPlayerState ?: return player
        if (!riichiState.isRiichi) return player
        return player.copy(playerRuleState = riichiState.copy(isPermanentlyFuriten = true))
    }

    /** 拔北時移出手牌的北：剛摸到的牌是北時優先拔這張，否則拔手中第一張北。 */
    override fun tileSetAsideBy(player: MahjongPlayer, action: GameAction.Extension): IdentifiedTile? {
        if (action != PULL_NORTH_GAME_ACTION) return null
        val hand = player.hand
        return hand.lastDrawn?.takeIf { it.tile.riichiCanonical == Tile.Honor.North }
            ?: hand.tiles.firstOrNull { it.tile.riichiCanonical == Tile.Honor.North }
    }

    /**
     * 拔北：把 [tileSetAsideBy] 選出的北移到拔北區並記錄拔北動作，剛摸到的牌併入手牌，同時讓所有人的一發失效。
     * 是否可以拔北由 [createLegalActionValidator] 判斷，這裡不再檢查。
     */
    override fun applyTileSetAsideAction(
        tableState: TableState,
        actorPlayerId: Uuid,
        action: GameAction.Extension,
    ): TableState? {
        val player = tableState.players.firstOrNull { it.id == actorPlayerId } ?: return null
        val riichiState = player.playerRuleState as? RiichiPlayerState ?: return null
        val north = tileSetAsideBy(player, action) ?: return null
        val hand = player.hand
        val remaining = (hand.tiles + listOfNotNull(hand.lastDrawn)).filterNot { it.id == north.id }
        val puller = player.copy(
            hand = hand.copy(tiles = remaining, lastDrawn = null),
            playerRuleState = riichiState.copy(nukiDoraTiles = riichiState.nukiDoraTiles + north),
        ).recordAction(action)
        val players = tableState.players.map { if (it.id == actorPlayerId) puller else it }.map { it.withoutIppatsu() }
        return tableState.copy(players = players)
    }

    /** 本局拔出的北。 */
    override fun setAsideTiles(player: MahjongPlayer): List<IdentifiedTile> = (player.playerRuleState as? RiichiPlayerState)?.nukiDoraTiles.orEmpty()

    /** 清除一發資格。 */
    private fun MahjongPlayer.withoutIppatsu(): MahjongPlayer {
        val riichiState = playerRuleState as? RiichiPlayerState ?: return this
        if (!riichiState.isIppatsu) return this
        return copy(playerRuleState = riichiState.copy(isIppatsu = false))
    }
}
