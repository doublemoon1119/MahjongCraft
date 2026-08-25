package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileOrder
import com.doublemoon1119.mahjongcraft.logic.config.DynamicRuleState
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.ExhaustiveDrawSettlementResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.RevealedHandSettlement
import com.doublemoon1119.mahjongcraft.logic.module.RoundInfoLine
import com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult
import com.doublemoon1119.mahjongcraft.logic.module.WinSettlementResult
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.layout.RiichiWallLayout
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.opening.RiichiWallOpeningPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.dora.getNextDora
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.tile.TileInterpretationPolicy
import com.doublemoon1119.mahjongcraft.logic.util.isHonor
import com.doublemoon1119.mahjongcraft.logic.util.isTerminal
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
) : MahjongRuleModule<RiichiRuleConfig> {
    /** 日本麻將手牌整理排序規則。 */
    override val tileOrder: TileOrder = RiichiTileOrder

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

    /** 建立將日麻赤五解讀為普通五的牌面 policy。 */
    override fun createTileInterpretationPolicy(): TileInterpretationPolicy = RiichiTileInterpretationPolicy

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
                mergeTsumoRemainder(tableState, player.id, mapOf(paoPlayerId to pointResult.paoPayment), pointResult.remainder)
            }

            // Ron / PaoRon 理論上不會出現在 isTsumo = true 的計算結果中。若真的發生，視為此規則
            // 無法對這次自摸完成結算，回傳 null 讓呼叫端以 IllegalAction 處理，而非產生錯誤的結算。
            is RiichiPointResult.Ron, is RiichiPointResult.PaoRon -> return null
        }

        return WinResolutionResult(
            settlement = WinSettlementResult(totalGained = result.totalPoint, paymentsByPlayerId = payments),
            handValueResult = result,
        )
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
                val paoPayments = if (paoPlayerId == discarderId) {
                    mapOf(discarderId to pointResult.paymentEach * 2)
                } else {
                    mapOf(discarderId to pointResult.paymentEach, paoPlayerId to pointResult.paymentEach)
                }
                mergeRonRemainder(discarderId, paoPayments, pointResult.remainder)
            }

            // DealerTsumo/NonDealerTsumo/PaoTsumo 理論上不會出現在 isTsumo = false 的計算結果中。
            // 若真的發生，視為此規則無法對這次榮和完成結算，回傳 null 讓呼叫端以 IllegalAction 處理，
            // 而非產生錯誤的結算。
            is RiichiPointResult.DealerTsumo, is RiichiPointResult.NonDealerTsumo, is RiichiPointResult.PaoTsumo -> return null
        }

        return WinResolutionResult(
            settlement = WinSettlementResult(totalGained = result.totalPoint, paymentsByPlayerId = payments),
            handValueResult = result,
        )
    }

    /**
     * 把包牌疊加役滿時的 [remainder]（正常自摸結算部分）疊加進 [basePayments]（包牌部分的付款），
     * 同一位玩家同時是兩種付款人時金額相加，不互相覆蓋。[remainder] 為 null（沒有疊加役滿）時原樣
     * 回傳 [basePayments]。
     */
    private fun mergeTsumoRemainder(
        tableState: TableState,
        winnerId: Uuid,
        basePayments: Map<Uuid, Int>,
        remainder: RiichiPointResult?,
    ): Map<Uuid, Int> {
        val remainderPayments: Map<Uuid, Int> = when (remainder) {
            null -> return basePayments
            is RiichiPointResult.DealerTsumo ->
                tableState.players.filter { it.id != winnerId }.associate { it.id to remainder.paymentPerNonDealer }
            is RiichiPointResult.NonDealerTsumo -> {
                val dealerId = tableState.players.first { it.currentWind == tableState.prevalentWind }.id
                tableState.players.filter { it.id != winnerId }
                    .associate { it.id to if (it.id == dealerId) remainder.dealerPayment else remainder.otherNonDealerPayment }
            }
            // buildPaoPointResult 的 remainder 在自摸情境下一律經由 buildPointResult(isTsumo = true)
            // 產生，只會是 DealerTsumo/NonDealerTsumo，僅作防呆。
            is RiichiPointResult.Ron, is RiichiPointResult.PaoTsumo, is RiichiPointResult.PaoRon -> return basePayments
        }
        return (basePayments.keys + remainderPayments.keys).associateWith { id ->
            (basePayments[id] ?: 0) + (remainderPayments[id] ?: 0)
        }
    }

    /**
     * 把包牌疊加役滿時的 [remainder]（正常榮和結算部分，恆由 [discarderId] 全額支付）疊加進
     * [basePayments]（包牌部分的付款），同一位玩家同時是兩種付款人時金額相加，不互相覆蓋。
     * [remainder] 為 null（沒有疊加役滿）時原樣回傳 [basePayments]。
     */
    private fun mergeRonRemainder(discarderId: Uuid, basePayments: Map<Uuid, Int>, remainder: RiichiPointResult?): Map<Uuid, Int> {
        // buildPaoPointResult 的 remainder 在榮和情境下一律經由 buildPointResult(isTsumo = false)
        // 產生，只會是 Ron，僅作防呆。
        val remainderAmount = (remainder as? RiichiPointResult.Ron)?.total ?: return basePayments
        return basePayments + (discarderId to (basePayments[discarderId] ?: 0) + remainderAmount)
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

    /**
     * 計算一般流局（牌山摸盡）的點數結算：聽牌判定（[ShantenResult] 非 [ShantenResult.NotTenpai]
     * 皆視為聽牌，`Complete` 理論上不會在流局判定時出現，僅作寬鬆處理），並依
     * [buildNotenPenaltyDeltas] 計算不聽罰符。流局滿貫已由 [resolveNagashiMangan] 在普通流局之前判定。
     */
    override fun declareExhaustiveDraw(tableState: TableState): ExhaustiveDrawSettlementResult {
        val shantenCalculator = createShantenCalculator()
        val shantenByPlayer = tableState.players.associateWith { shantenCalculator.calculate(it.hand) }
        val tenpaiIds = shantenByPlayer.filterValues { it !is ShantenResult.NotTenpai }.keys.map { it.id }.toSet()

        return ExhaustiveDrawSettlementResult(
            reason = RiichiExhaustiveDrawReason.Normal,
            tenpaiPlayerIds = tenpaiIds,
            revealedHands = shantenByPlayer.mapNotNull { (player, result) ->
                val waits = (result as? ShantenResult.Tenpai)?.winningTiles ?: return@mapNotNull null
                RevealedHandSettlement(player.id, waits.toSet())
            },
            stickPotCollectorPlayerIds = emptySet(),
            scoreDeltas = buildNotenPenaltyDeltas(tableState, tenpaiIds),
        )
    }

    /**
     * 判定流局滿貫並計算自摸滿貫式收支；沒有任何人成立時回傳 `null`。
     *
     * 成立條件為牌河非空、全部是么九牌且從未被鳴走。此函式不修改桌況，也不收取供託。
     */
    fun resolveNagashiMangan(tableState: TableState): NagashiManganResolution? {
        val achieverIds = tableState.players
            .filter { player ->
                player.discardPile.entries.isNotEmpty() &&
                    player.discardPile.entries.all { entry ->
                        !entry.isTaken && (entry.tile.tile.isTerminal || entry.tile.tile.isHonor)
                    }
            }
            .mapTo(linkedSetOf()) { it.id }
        if (achieverIds.isEmpty()) return null
        return NagashiManganResolution(achieverIds, buildNagashiManganDeltas(tableState, achieverIds))
    }

    /** 九種九牌需要公開宣告者手牌作為成立證明，其餘途中流局不公開手牌。 */
    override fun resolveAbortiveDrawRevealedHands(
        tableState: TableState,
        declarerId: Uuid?,
        reason: ExhaustiveDrawReason,
    ): List<RevealedHandSettlement> {
        if (reason != RiichiExhaustiveDrawReason.KyuushuKyuuhai || declarerId == null) return emptyList()
        if (tableState.players.none { it.id == declarerId }) return emptyList()
        return listOf(RevealedHandSettlement(declarerId, emptySet()))
    }

    /**
     * 流局滿貫視為自摸滿貫，重用既有的 [PointCalculator.calculateNonYakumanPoint]（`han = 5`
     * 固定走滿貫的 `basicPoint = 2000` 分支，`fu` 不影響結果），依莊/閒身分換算成付款 map；
     * 多位成立者時（罕見邊界情況）比照多家和的既有作法，把各自的付款加總到同一份 `scoreDeltas`
     * （同一位玩家可能同時是多位成立者的付款對象）。
     */
    private fun buildNagashiManganDeltas(tableState: TableState, nagashiManganIds: Set<Uuid>): Map<Uuid, Int> {
        val deltas = tableState.players.associate { it.id to 0 }.toMutableMap()
        nagashiManganIds.forEach { achieverId ->
            val achiever = tableState.players.first { it.id == achieverId }
            val isDealer = achiever.currentWind == Wind.EAST
            val pointResult = PointCalculator.calculateNonYakumanPoint(han = 5, fu = 0, isDealer = isDealer, isTsumo = true)
            val payments: Map<Uuid, Int> = when (pointResult) {
                is RiichiPointResult.DealerTsumo ->
                    tableState.players
                        .filter { it.id != achieverId }
                        .associate { it.id to pointResult.paymentPerNonDealer }

                is RiichiPointResult.NonDealerTsumo -> {
                    val dealerId = tableState.players.first { it.currentWind == Wind.EAST }.id
                    tableState.players
                        .filter { it.id != achieverId }
                        .associate { it.id to if (it.id == dealerId) pointResult.dealerPayment else pointResult.otherNonDealerPayment }
                }

                // calculateNonYakumanPoint(isTsumo = true) 理論上只會回傳上述兩種結果，僅作防呆。
                is RiichiPointResult.Ron, is RiichiPointResult.PaoTsumo, is RiichiPointResult.PaoRon -> emptyMap()
            }

            deltas[achieverId] = (deltas[achieverId] ?: 0) + payments.values.sum()
            payments.forEach { (payerId, amount) -> deltas[payerId] = (deltas[payerId] ?: 0) - amount }
        }
        return deltas
    }

    /**
     * 不聽罰符：總額為「[RiichiScoreConfig.notenPenaltyUnit] * (對局人數 - 1)」（四人對局標準值
     * 3000），由聽牌者均分收取、不聽者均分支付（皆為淨額，不需要逐筆算聽牌者與不聽者之間的
     * 個別配對金額）。無人聽牌、全員聽牌、或（理論上不會發生的）無人不聽時，回傳空 map
     * （沒有任何點數交換）。
     */
    private fun buildNotenPenaltyDeltas(tableState: TableState, tenpaiIds: Set<Uuid>): Map<Uuid, Int> {
        val notenIds = tableState.players.map { it.id }.toSet() - tenpaiIds
        if (tenpaiIds.isEmpty() || notenIds.isEmpty()) return emptyMap()

        val total = config.scoreConfig.notenPenaltyUnit * (tableState.playerCount - 1)
        val gainPerTenpai = total / tenpaiIds.size
        val lossPerNoten = total / notenIds.size

        return tenpaiIds.associateWith { gainPerTenpai } + notenIds.associateWith { -lossPerNoten }
    }

    /**
     * 多家和判定為流局時，日本麻將對應的具體流局原因固定為三家和了。
     */
    override fun resolveMultiRonAbortiveDraw(): ExhaustiveDrawReason = RiichiExhaustiveDrawReason.SanchaHou

    /**
     * 四風連打：全場尚未有人鳴牌，且全員恰好都打過一張牌（`entries.singleOrNull()` 只有在這個
     * 情境下才會全員非 null），這些第一張捨牌若皆為同一種風牌則成立。
     */
    override fun resolveSuufonRenda(tableStateAfterDiscard: TableState): ExhaustiveDrawReason? {
        if (tableStateAfterDiscard.players.any { it.hand.exposedMelds.isNotEmpty() }) return null
        val firstDiscards = tableStateAfterDiscard.players.map { it.discardPile.entries.singleOrNull()?.tile?.tile }
        if (firstDiscards.any { it == null || !it.isWind }) return null
        return if (firstDiscards.toSet().size == 1) RiichiExhaustiveDrawReason.SuufonRenda else null
    }

    /**
     * 四家立直：全員皆已宣告立直則成立。呼叫端只在剛套用完一次立直宣告、且確定沒人反應時才會
     * 呼叫這個方法，所以「全員皆立直」這個條件只可能在恰好完成的那次宣告變成 true
     * （玩家只能宣告立直一次，見 [declareRiichi] 的 `!isRiichi` 合法性檢查）。
     */
    override fun resolveSuuchaRiichi(tableStateAfterDeclaration: TableState): ExhaustiveDrawReason? {
        val allRiichi = tableStateAfterDeclaration.players.all { (it.playerRuleState as? RiichiPlayerState)?.isRiichi == true }
        return if (allRiichi) RiichiExhaustiveDrawReason.SuuchaRiichi else null
    }

    /**
     * 四槓散了：逐玩家算出各自的槓子數（明槓/暗槓/加槓皆算）加總得到全場總數，未滿 4 個不成立；
     * 達到 4 個（含）以上時，若其中有一位玩家的槓子數就等於全場總數，代表全部槓子都是他一人
     * 達成（該玩家可能正在做四槓子役滿），此時不成立。
     */
    override fun resolveSuukanNagare(tableState: TableState): ExhaustiveDrawReason? {
        val kanCountsByPlayer = tableState.players.map { player ->
            player.hand.exposedMelds.count {
                it.type == MeldType.OPEN_KAN || it.type == MeldType.ADDED_KAN || it.type == MeldType.CLOSED_KAN
            }
        }
        val totalKans = kanCountsByPlayer.sum()
        if (totalKans < 4) return null

        val isSinglePlayerAllKans = kanCountsByPlayer.any { it == totalKans }
        return if (isSinglePlayerAllKans) null else RiichiExhaustiveDrawReason.SuukanNagare
    }

    /**
     * 擊飛（飛び/Tobi）：任一玩家分數低於 0 分時，立即強制結束整場對局，不再繼續連莊或換莊。
     */
    override fun hasAdditionalMatchEndCondition(tableState: TableState): Boolean = tableState.players.any { it.score < 0 }

    /**
     * 日本麻將的特殊視覺強調對象是寶牌：赤寶牌（[RiichiTileInterpretationPolicy.isRedDora]，跟指示牌
     * 無關的獨立判斷）或符合任一 [revealedWallTiles]（寶牌指示牌）下一張的牌（[getNextDora]，兩張牌
     * 需先各自轉成 [riichiCanonical] 再比較，理由同 [createTileInterpretationPolicy] 的既有慣例）。
     */
    override fun isHighlightedTile(tile: Tile, revealedWallTiles: List<Tile>): Boolean = RiichiTileInterpretationPolicy.isRedDora(tile) ||
        revealedWallTiles.any { indicator -> tile.riichiCanonical == getNextDora(indicator).riichiCanonical }

    /**
     * 直接查詢 [RiichiPlayerState.isRiichi]，轉型手法同 [resolveSuuchaRiichi]。
     */
    override fun isPlayerInRiichi(player: MahjongPlayer): Boolean = (player.playerRuleState as? RiichiPlayerState)?.isRiichi ?: false

    /**
     * 直接讀取 [RiichiDynamicState.riichiStickCount]，不像 [collectStickPot] 會連帶歸零。
     */
    override fun getStickPotCount(tableState: TableState): Int = (tableState.dynamicRuleState as? RiichiDynamicState)?.riichiStickCount ?: 0

    /**
     * 日麻桌面局況顯示依序：場風＋局數＋本場數合併一行（標題）、立直棒累積供託數量（沒有供託時
     * 略過這行，不顯示「供託：0」）、牌山剩餘張數（固定排在最後）。
     */
    override fun getRoundInfoLines(tableState: TableState): List<RoundInfoLine> {
        val stickPotCount = getStickPotCount(tableState)
        return listOfNotNull(
            RoundInfoLine(TITLE_KEY, listOf(tableState.prevalentWind.ordinal, tableState.localRoundNumber, tableState.comboCount)),
            if (stickPotCount > 0) RoundInfoLine(STICK_POT_KEY, listOf(stickPotCount)) else null,
            RoundInfoLine(WALL_REMAINING_KEY, listOf(tableState.tileWall.remainingCount)),
        )
    }

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
        /** [getRoundInfoLines] 場風＋局數＋本場數合併標題行的 key，供呈現層辨識。 */
        const val TITLE_KEY = "riichiTitle"

        /** [getRoundInfoLines] 牌山剩餘張數行的 key，供呈現層辨識。 */
        const val WALL_REMAINING_KEY = "riichiWallRemaining"

        /** [getRoundInfoLines] 立直棒累積供託數量行的 key，供呈現層辨識。 */
        const val STICK_POT_KEY = "riichiStickPot"
    }
}
