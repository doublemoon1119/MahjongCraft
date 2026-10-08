package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiPhysicalWallLayoutPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.opening.RiichiWallOpeningPolicy
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
import com.doublemoon1119.mahjongcraft.logic.util.isWind
import kotlin.uuid.Uuid

/**
 * 日本麻將規則模組實作。
 *
 * 負責串接日本麻將特有的組件，包含 [RiichiWallFactory] 與 [RiichiDiscardPile]...等等。
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

    /** 建立日本麻將本局自動操作 policy。 */
    override fun createAutomaticControlPolicy(): RiichiAutomaticControlPolicy = RiichiAutomaticControlPolicy

    /** 日麻只公開已經能從桌面立直棒觀察到的立直狀態。 */
    override fun getPublicPlayerIndicators(tableState: TableState, player: MahjongPlayer): List<PublicPlayerIndicator> = if (isPlayerInRiichi(player)) listOf(PublicPlayerIndicator(RIICHI_INDICATOR_ID)) else emptyList()

    /** 日本麻將手牌整理排序規則。 */
    override val tileOrder: TileOrder = RiichiTileOrder

    /** 建立支援南入、西入、驟死、和了止め與擊飛的日麻 progression policy。 */
    override fun createMatchProgressionPolicy(): MatchProgressionPolicy = RiichiMatchProgressionPolicy(config)

    /**
     * 建立日本麻將牌山工廠。
     *
     * @return [RiichiWallFactory] 實體。
     */
    override fun createWallFactory(): RiichiWallFactory = RiichiWallFactory(config)

    /** 建立四人日本麻將的雙骰牌牆開門 policy。 */
    override fun createWallOpeningPolicy(): RiichiWallOpeningPolicy = RiichiWallOpeningPolicy

    /** 建立四人日本麻將固定 136 張的牌牆布局。 */
    override fun createWallLayout(): RiichiWallLayout = RiichiWallLayout(config)

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

    /**
     * 建立日本麻將的初始動態桌況狀態。
     *
     * @return 全新的 [RiichiDynamicState]（立直棒數量為 0）。
     */
    override fun createInitialDynamicState(): RiichiDynamicState = RiichiDynamicState()

    /**
     * 只延續尚未被收下的供託／立直棒；槓次與槓寶牌公開進度都只屬於單局，換局一律歸零。
     *
     * 這些每局欄位若跨局殘留，新局會以為已經槓過：指示牌索引往深處位移、公開張數多算，而且會吃掉
     * 新局本來可槓的次數。
     */
    override fun createNextRoundDynamicState(previous: DynamicRuleState?): RiichiDynamicState = RiichiDynamicState(riichiStickCount = (previous as? RiichiDynamicState)?.riichiStickCount ?: 0)

    /**
     * 建立日本麻將的初始玩家規則狀態。
     *
     * @return 全新的 [RiichiPlayerState]（尚未立直、無包牌責任）。
     */
    override fun createInitialPlayerRuleState(): RiichiPlayerState = RiichiPlayerState()

    /**
     * 日本麻將沒有摸牌後需要變更的規則特有狀態；立直後的下一次摸牌仍在一發期限內，可以自摸一發。
     *
     * @return 固定回傳 [player] 本身。
     */
    override fun onPlayerDrew(player: MahjongPlayer): MahjongPlayer = player

    /**
     * 若玩家已立直且仍有一發資格，這次捨牌代表一發的期限已經結束（立直後一巡內未能胡牌），故清除一發資格。
     */
    override fun onPlayerDiscarded(player: MahjongPlayer): MahjongPlayer {
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
     *
     * 吃不構成包牌，直接回傳 [claimingPlayer]。
     */
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
            RiichiHandValueContextCalculator.Input(
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
            RiichiHandValueContextCalculator.Input(
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

    /**
     * 收下場上所有立直棒：贏家獲得「立直棒數量 × [RIICHI_STICK_POINTS]」點，收下後立直棒數量歸零。
     *
     * @return 若 [tableState] 的動態桌況狀態並非 [RiichiDynamicState]（理論上不會發生，僅作防呆），
     *         則回傳 null。
     */
    override fun collectStickPot(tableState: TableState): Pair<DynamicRuleState?, Int>? {
        val riichiDynamicState = tableState.dynamicRuleState as? RiichiDynamicState ?: return null
        return riichiDynamicState.copy(riichiStickCount = 0) to riichiDynamicState.riichiStickCount * RIICHI_STICK_POINTS
    }

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
    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult = riichiExhaustiveDrawSettlement(tableState, createShantenCalculator(), config.scoreConfig.notenPenaltyUnit)

    /**
     * 判定流局滿貫並計算自摸滿貫式收支；沒有任何人成立時回傳 `null`。
     *
     * 成立條件為牌河非空、全部是么九牌且從未被鳴走。此函式不修改桌況，也不收取供託。
     *
     * 只計入 [TableState.activePlayers]：本局已胡牌退場的玩家不再參與流局計算，其牌河也不該成立
     * 流局滿貫。
     */
    override fun resolveNagashiMangan(tableState: TableState): NagashiManganResolution? = riichiNagashiMangan(tableState)

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
     * 只應在確定這次捨牌沒有任何人可以吃/碰/槓/榮和之後才呼叫；呼叫時機由呼叫端掌控，這裡只負責純邏輯判定。
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
     */
    fun resolveSuuchaRiichi(tableStateAfterDeclaration: TableState): ExhaustiveDrawReason? {
        val allRiichi = tableStateAfterDeclaration.players.all { (it.playerRuleState as? RiichiPlayerState)?.isRiichi == true }
        return if (allRiichi) RiichiExhaustiveDrawReason.SuuchaRiichi else null
    }

    /**
     * 四槓散了：逐玩家算出各自的槓子數（明槓/暗槓/加槓皆算）加總得到全場總數，未滿 4 個不成立；
     * 達到 4 個（含）以上時，若其中有一位玩家的槓子數就等於全場總數，代表全部槓子都是他一人
     * 達成（該玩家可能正在做四槓子役滿），此時不成立。
     */
    override fun resolveSuukanNagare(tableState: TableState): ExhaustiveDrawReason? = riichiSuukanNagare(tableState)

    /**
     * 日本麻將的加值牌是寶牌：赤寶牌（[RiichiTileInterpretationPolicy.isRedDora]，跟指示牌
     * 無關的獨立判斷）或符合任一 [revealedWallTiles]（寶牌指示牌）下一張的牌（[getNextDora]，兩張牌
     * 需先各自轉成 [riichiCanonical] 再比較，理由同 [createTileInterpretationPolicy] 的既有慣例）。
     */
    override fun isBonusTile(tile: Tile, revealedWallTiles: List<Tile>): Boolean = RiichiTileInterpretationPolicy.isRedDora(tile) ||
        revealedWallTiles.any { indicator -> tile.riichiCanonical == getNextDora(indicator).riichiCanonical }

    /** 這位玩家目前是否立直中；直接查詢 [RiichiPlayerState.isRiichi]。 */
    fun isPlayerInRiichi(player: MahjongPlayer): Boolean = (player.playerRuleState as? RiichiPlayerState)?.isRiichi ?: false

    /**
     * 立直後只能摸切，因此回傳玩家剛摸入的牌；未立直或尚未摸牌時不額外限制。
     */
    override fun forcedDiscardTileId(tableState: TableState, player: MahjongPlayer): Uuid? = player.hand.lastDrawn?.id?.takeIf { isPlayerInRiichi(player) }

    /** 場上尚未被收下的供託支數；直接讀取 [RiichiDynamicState.riichiStickCount]，不像 [collectStickPot] 會連帶歸零。 */
    fun getStickPotCount(tableState: TableState): Int = (tableState.dynamicRuleState as? RiichiDynamicState)?.riichiStickCount ?: 0

    /**
     * 只有立直中的玩家才需要記錄永久振聽——未立直時放過和牌只構成一般同巡振聽，不需要這個永久旗標，
     * 轉型手法同 [isPlayerInRiichi]。
     */
    override fun onPlayerDeclinedWin(player: MahjongPlayer): MahjongPlayer {
        val riichiState = player.playerRuleState as? RiichiPlayerState ?: return player
        if (!riichiState.isRiichi) return player
        return player.copy(playerRuleState = riichiState.copy(isPermanentlyFuriten = true))
    }

    companion object {
        /** 玩家已公開宣告立直的 indicator ID。 */
        const val RIICHI_INDICATOR_ID = "mahjongcraft:riichi/riichi_indicator"
    }
}
