package com.doublemoon1119.mahjongcraft.ai.expectation

import com.doublemoon1119.mahjongcraft.ai.AiDecisionContext
import com.doublemoon1119.mahjongcraft.ai.AiDecisionPhase
import com.doublemoon1119.mahjongcraft.ai.ExtensionGameActionAiRegistry
import com.doublemoon1119.mahjongcraft.ai.toOwnHand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.toSnapshot
import com.doublemoon1119.mahjongcraft.logic.module.DeclarationEffect
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.module.PositionView
import kotlin.math.min

/**
 * 一次 AI 決策的期望值計算。
 *
 * 每個選項都換算成動作完成後的手牌再比較：
 *
 * > 期望值 ＝ 和牌期望 － 後續風險 － 立即放銃損失 － 宣告成本
 *
 * - 可換牌的手牌：之後可以隨時改打安全牌，因此「和牌期望 － 後續風險」最低為 0。
 * - 宣告後不能換牌的手牌：之後每一輪都要打出摸到的牌，後續風險一律計入。
 * - 鳴牌、過與槓牌之後都還要再打出一張牌，因此以其中最安全的一張計算立即放銃損失；
 *   鳴牌只在鳴牌後的手牌為聽牌或一向聽時列入候選，因為只有這兩種情況會由規則實際判斷能不能和牌。
 * - 兩向聽以上使用規則的基準打點，但不超過同一次決策中以實際手牌計算打點的選項（聽牌、一向聽）的平均每次和牌點數，
 *   避免拆掉接近聽牌的手牌只因為換成基準打點而顯得有利。
 * - 名次換算只用於和牌與放銃的點數；宣告成本在和牌時會以打點的一部分收回，因此以點數計算、不換算名次。
 * - 能和牌時一律和牌；可宣告途中流局時，最佳選項的和牌機率低於
 *   [ExpectationParameters.abortiveDrawWinProbability] 就宣告。
 * - 期望值差距小於 [EXPECTED_VALUE_TIE_TOLERANCE] 時選擇候選清單中較前面的選項：過優先於鳴牌；
 *   捨牌依規則牌序排列，同一種牌優先打出剛摸到的那張；一般捨牌優先於附帶宣告的捨牌，再其次為槓。
 *
 * 規則擴充動作只評估 handler 說明了會打出哪張牌的候選；其他擴充命令無法評估，不會被選擇。
 *
 * @property level 可使用的資訊範圍。
 * @property parameters 估計參數。
 * @property module 本局的規則模組。
 * @property opponentModel 本局規則的對手模型。
 * @property context 本次決策的情境。
 */
internal class ExpectedValueEvaluator(
    private val level: InformationLevel,
    private val parameters: ExpectationParameters,
    private val module: MahjongRuleModule<*>,
    private val opponentModel: OpponentModel,
    private val context: AiDecisionContext,
) {
    /** 以評估者本人為觀察者的快照。 */
    private val snapshot = context.snapshot

    /** 評估者本人。 */
    private val selfId = context.selfId

    /** 評估者自己的完整手牌。 */
    private val ownHand: Hand = snapshot.players.first { it.id == selfId }.hand.toOwnHand()

    /** 目前局面的視角。 */
    private val currentView = PositionView(snapshot = snapshot, evaluatorId = selfId)

    /** 本局規則的規則查詢。 */
    private val rules = module.createPositionRules()

    /** 點數與名次的換算。 */
    private val placement = PlacementUtility.from(
        snapshot = snapshot,
        selfId = selfId,
        ranking = module.compareForMatchRanking(),
        considersPlacement = level.considersPlacement,
        stepPoints = parameters.placementStepPoints,
    )

    /** 評估者眼中的未見牌。 */
    private val unseen = UnseenTileCounts.from(
        snapshot = snapshot,
        selfId = selfId,
        module = module,
        countsVisibleTiles = level.countsVisibleTiles,
    )

    /** 本局剩餘的和牌機會。 */
    private val outlook = DrawOutlook.from(snapshot, parameters)

    /** 手牌和牌前景的評估。 */
    private val assessor = HandAssessor(
        level = level,
        parameters = parameters,
        selfId = selfId,
        rules = rules,
        opponentModel = opponentModel,
        shantenCalculator = module.createShantenCalculator(),
        interpretation = module.createTileInterpretationPolicy(),
        tileOrder = module.tileOrder,
        unseen = unseen,
        outlook = outlook,
        placement = placement,
        flatWinValue = placement.gain(opponentModel.baselineWinValue(currentView, selfId).toDouble()),
    )

    /** 列入防守計算的對手。 */
    private val defendedThreats = threats()

    /** 打出一張牌的放銃期望損失。 */
    private val risk = DealInRisk(
        threats = defendedThreats,
        danger = { opponentId, tile -> opponentModel.discardDanger(currentView, opponentId, tile) },
    )

    /** 估計後續風險時使用的放銃期望損失；依 [ExpectationParameters.futureRiskHighThreatOnly] 只計高威脅對手。 */
    private val laterDiscardRisk = if (parameters.futureRiskHighThreatOnly) {
        DealInRisk(
            threats = defendedThreats.filter { it.readyProbability >= parameters.highThreatReadyProbability },
            danger = { opponentId, tile -> opponentModel.discardDanger(currentView, opponentId, tile) },
        )
    } else {
        risk
    }

    /** 下一個假設視角的編號。 */
    private var nextViewId = 0

    /** 依 [AiDecisionContext.phase] 決定要送出的命令。 */
    fun decide(extensionRegistry: ExtensionGameActionAiRegistry): GameCommand = when (context.phase) {
        AiDecisionPhase.OwnTurn -> decideOwnTurn(extensionRegistry)
        AiDecisionPhase.RespondingToDiscard -> decideDiscardResponse()
        AiDecisionPhase.RespondingToKan -> GameCommand.RespondToKan(
            context.legalActions.firstOrNull { it is GameAction.Ron } ?: GameAction.Pass,
        )
    }

    /**
     * 一個選項套用打點上限前的評估。
     *
     * @property candidate 被評估的選項。
     * @property hand 選項完成後的手牌。
     * @property assessment 手牌的和牌前景。
     * @property locksHand 選項完成後是否不能再換牌。
     * @property fixedLoss 與和牌前景無關的立即損失：放銃期望損失與宣告成本。
     */
    private data class Evaluation(
        val candidate: DecisionCandidate,
        val hand: Hand,
        val assessment: HandAssessment,
        val locksHand: Boolean,
        val fixedLoss: Double,
    )

    /** 評估並選出最佳選項；[candidates] 中至少要有一個可評估的選項。 */
    fun choose(candidates: List<DecisionCandidate>): CandidateScore {
        var best: CandidateScore? = null
        scoreAll(candidates).forEach { score ->
            val current = best
            if (current == null || score.expectedValue > current.expectedValue + EXPECTED_VALUE_TIE_TOLERANCE) {
                best = score
            }
        }
        return checkNotNull(best) { "No evaluable decision candidate" }
    }

    /** 評估 [candidates] 中可比較的選項，依原本的順序回傳；兩向聽以上的打點套用同一次決策的上限。 */
    fun scoreAll(candidates: List<DecisionCandidate>): List<CandidateScore> {
        val evaluations = candidates.mapNotNull { evaluate(it) }
        val valueCap = exactValuePerWin(evaluations)
        return evaluations.map { scoreOf(it, valueCap) }
    }

    /** 評估一個選項；不列入比較的選項回傳 null。 */
    private fun evaluate(candidate: DecisionCandidate): Evaluation? = when (candidate) {
        is DecisionCandidate.Pass -> evaluateWaitingHand(candidate, ownHand)
        is DecisionCandidate.Discard -> evaluateDiscard(candidate)
        is DecisionCandidate.Claim -> evaluateClaim(candidate)
        is DecisionCandidate.SelfKan -> selfKanHand(candidate)?.let { evaluateWaitingHand(candidate, it) }
    }

    /** 以實際手牌計算打點的評估中，依和牌機率加權的平均每次和牌點數；沒有這類評估時為 null。 */
    private fun exactValuePerWin(evaluations: List<Evaluation>): Double? {
        val exact = evaluations.map { it.assessment }.filter { it.estimatedValuePerWin == null && it.outlook.winProbability > 0 }
        val probability = exact.sumOf { it.outlook.winProbability }
        if (probability <= 0) return null
        return exact.sumOf { it.outlook.expectedValue } / probability
    }

    /** 套用打點上限 [valueCap] 後的期望值。 */
    private fun scoreOf(evaluation: Evaluation, valueCap: Double?): CandidateScore {
        val assessment = valueCap?.let { evaluation.assessment.cappedAt(it) } ?: evaluation.assessment
        return CandidateScore(
            candidate = evaluation.candidate,
            expectedValue = handValue(evaluation.hand, assessment, evaluation.locksHand) - evaluation.fixedLoss,
            winProbability = assessment.outlook.winProbability,
        )
    }

    /** 自己回合：能自摸就自摸，否則比較捨牌、附帶宣告的捨牌與槓。 */
    private fun decideOwnTurn(extensionRegistry: ExtensionGameActionAiRegistry): GameCommand {
        if (GameAction.Tsumo in context.legalActions) return GameCommand.Tsumo
        val best = choose(discardCandidates() + declarationCandidates(extensionRegistry) + selfKanCandidates())
        val abortiveDraw = context.legalActions.filterIsInstance<GameAction.ExhaustiveDraw>().firstOrNull()
        if (abortiveDraw != null && best.winProbability < parameters.abortiveDrawWinProbability) {
            return GameCommand.DeclareExhaustiveDraw(abortiveDraw.reason)
        }
        return best.candidate.command
    }

    /** 回應他家捨牌：能榮和就榮和，否則比較過與各種鳴牌。 */
    private fun decideDiscardResponse(): GameCommand {
        context.legalActions.firstOrNull { it is GameAction.Ron }?.let { return GameCommand.RespondToDiscard(it) }
        val pass = DecisionCandidate.Pass(GameCommand.RespondToDiscard(GameAction.Pass))
        return choose(listOf(pass) + claimCandidates()).candidate.command
    }

    /** 一般捨牌：每種牌一個候選，依規則牌序排列；同一種牌優先打出剛摸到的那張。規則強制捨牌時只有指定的牌。 */
    private fun discardCandidates(): List<DecisionCandidate> {
        val forced = context.forcedDiscardTileId
        val lastDrawnId = snapshot.players.first { it.id == selfId }.hand.lastDrawn?.id
        val tiles = if (forced != null) {
            ownHand.tiles.filter { it.id == forced }
        } else {
            ownHand.tiles
                .groupBy { it.tile }
                .values
                .map { group -> group.firstOrNull { it.id == lastDrawnId } ?: group.first() }
                .sortedWith(compareBy(module.tileOrder) { it.tile })
        }
        return tiles.map { DecisionCandidate.Discard(tile = it, declaration = null, command = GameCommand.Discard(it.id)) }
    }

    /** 規則擴充動作中說明了會打出哪張牌的候選。 */
    private fun declarationCandidates(extensionRegistry: ExtensionGameActionAiRegistry): List<DecisionCandidate> = context.legalActions
        .filterIsInstance<GameAction.Extension>()
        .flatMap { extensionRegistry.createCandidates(it.value, context) }
        .mapNotNull { candidate ->
            val tileId = candidate.discardTileId ?: return@mapNotNull null
            if (context.forcedDiscardTileId != null && context.forcedDiscardTileId != tileId) return@mapNotNull null
            val tile = ownHand.tiles.firstOrNull { it.id == tileId } ?: return@mapNotNull null
            DecisionCandidate.Discard(tile = tile, declaration = candidate.declaration, command = candidate.command)
        }

    /** 自己回合的暗槓與加槓。 */
    private fun selfKanCandidates(): List<DecisionCandidate> = context.legalActions
        .filterIsInstance<GameAction.Kan>()
        .filter { it.type != GameAction.KanType.OPEN_KAN }
        .map { kan ->
            DecisionCandidate.SelfKan(
                type = kan.type,
                tileIds = listOf(kan.tileId) + kan.withTiles,
                command = GameCommand.Kan(kan.type, kan.tileId),
            )
        }

    /** 吃、碰與明槓。 */
    private fun claimCandidates(): List<DecisionCandidate> = context.legalActions.mapNotNull { action ->
        val (meldType, tileId, withTiles) = when (action) {
            is GameAction.Chi -> Triple(MeldType.CHI, action.tileId, action.withTiles)
            is GameAction.Pon -> Triple(MeldType.PON, action.tileId, action.withTiles)
            is GameAction.Kan -> if (action.type == GameAction.KanType.OPEN_KAN) {
                Triple(MeldType.OPEN_KAN, action.tileId, action.withTiles)
            } else {
                return@mapNotNull null
            }
            else -> return@mapNotNull null
        }
        val claimedTile = snapshot.players
            .flatMap { it.discardPile.entries }
            .firstOrNull { it.tile.id == tileId }
            ?.tile
            ?: return@mapNotNull null
        DecisionCandidate.Claim(
            meldType = meldType,
            claimedTile = claimedTile,
            handTileIds = withTiles,
            command = GameCommand.RespondToDiscard(action),
        )
    }

    /** 打出一張牌（可附帶宣告）後的評估。 */
    private fun evaluateDiscard(candidate: DecisionCandidate.Discard): Evaluation {
        val rest = ownHand.copy(tiles = ownHand.tiles.filterNot { it.id == candidate.tile.id })
        val declarations = setOfNotNull(candidate.declaration)
        val effect = candidate.declaration?.let { rules.declarationEffect(currentView, it) } ?: DeclarationEffect.NONE
        return Evaluation(
            candidate = candidate,
            hand = rest,
            assessment = assessor.assess(rest, hypotheticalView(rest, discarded = candidate.tile), declarations),
            locksHand = effect.locksHand,
            fixedLoss = risk.immediateLoss(assessor.canonical(candidate.tile.tile)) + effect.cost,
        )
    }

    /** 鳴牌後的評估；非槓的副露之後打出最佳的一張牌。鳴牌後無法聽牌或一向聽時不列入比較。 */
    private fun evaluateClaim(candidate: DecisionCandidate.Claim): Evaluation? {
        val meldTiles = ownHand.tiles.filter { it.id in candidate.handTileIds } + candidate.claimedTile
        val called = Hand(
            tiles = ownHand.tiles.filterNot { it.id in candidate.handTileIds },
            melds = ownHand.melds + Meld(
                type = candidate.meldType,
                tiles = meldTiles,
                sourceTile = candidate.claimedTile,
                sourceDirection = RelativeDirection.Left,
            ),
        )
        if (candidate.meldType == MeldType.OPEN_KAN) {
            return if (assessor.shantenOf(called) > 1) null else evaluateWaitingHand(candidate, called)
        }
        return distinctDiscardIndices(called)
            .mapNotNull { index ->
                val discarded = called.tiles[index]
                val rest = called.withoutTileAt(index)
                if (assessor.shantenOf(rest) > 1) return@mapNotNull null
                Evaluation(
                    candidate = candidate,
                    hand = rest,
                    assessment = assessor.assess(rest, hypotheticalView(rest, discarded = discarded), declarations = emptySet()),
                    locksHand = false,
                    fixedLoss = risk.immediateLoss(assessor.canonical(discarded.tile)),
                )
            }
            .maxByOrNull { scoreOf(it, valueCap = null).expectedValue }
    }

    /** 暫時不打牌的手牌（過、槓）的評估；之後要打出的牌以最安全的一張計算。 */
    private fun evaluateWaitingHand(candidate: DecisionCandidate, hand: Hand): Evaluation = Evaluation(
        candidate = candidate,
        hand = hand,
        assessment = assessor.assess(hand, hypotheticalView(hand, discarded = null), declarations = emptySet()),
        locksHand = false,
        fixedLoss = risk.minimumLoss(hand.tiles.map { assessor.canonical(it.tile) }),
    )

    /** 和牌期望扣除後續風險；可換牌的手牌最低為 0。 */
    private fun handValue(
        hand: Hand,
        assessment: HandAssessment,
        locksHand: Boolean,
    ): Double {
        val attackValue = assessment.outlook.expectedValue - futureRisk(hand, assessment, locksHand)
        return if (locksHand) attackValue else attackValue.coerceAtLeast(0.0)
    }

    /**
     * 繼續進攻時，之後打出的牌的放銃風險。
     *
     * 宣告後不能換牌時，以未見牌的平均損失乘上預期進行的輪數，再乘上 [ExpectationParameters.lockedFutureRiskFactor]；
     * 可換牌時，依 [ExpectationParameters.futureRiskTiles] 估計之後打出的牌。
     */
    private fun futureRisk(
        hand: Hand,
        assessment: HandAssessment,
        locksHand: Boolean,
    ): Double {
        if (!level.considersFutureRisk) return 0.0
        if (locksHand) {
            val cycles = assessment.tenpaiProfile?.expectedCyclesInPlay(outlook.ownDraws, outlook) ?: outlook.ownDraws.toDouble()
            return laterDiscardRisk.averageLoss(unseen) * cycles * parameters.lockedFutureRiskFactor
        }
        val turns = min(outlook.ownDraws, (assessment.shanten.coerceAtLeast(0) + 1) * parameters.attackTurnsPerShanten)
        val tiles = hand.tiles.map { assessor.canonical(it.tile) }
        return when (parameters.futureRiskTiles) {
            FutureRiskTiles.AVERAGE -> laterDiscardRisk.averageLoss(tiles) * turns
            FutureRiskTiles.SAFEST -> laterDiscardRisk.safestLosses(tiles, turns)
        }
    }

    /** 暗槓或加槓後的手牌；手牌中找不到對應的牌時為 null。 */
    private fun selfKanHand(candidate: DecisionCandidate.SelfKan): Hand? {
        val kanTiles = ownHand.tiles.filter { it.id in candidate.tileIds }
        if (kanTiles.size != candidate.tileIds.size) return null
        return when (candidate.type) {
            GameAction.KanType.CLOSED_KAN -> Hand(
                tiles = ownHand.tiles.filterNot { it.id in candidate.tileIds },
                melds = ownHand.melds + Meld(
                    type = MeldType.CLOSED_KAN,
                    tiles = kanTiles,
                    sourceTile = null,
                    sourceDirection = RelativeDirection.Self,
                ),
            )

            GameAction.KanType.ADDED_KAN -> {
                val added = kanTiles.single()
                val kind = assessor.canonical(added.tile)
                val meldIndex = ownHand.melds.indexOfFirst { meld ->
                    meld.type == MeldType.PON && meld.tiles.all { assessor.canonical(it.tile) == kind }
                }
                if (meldIndex < 0) null else ownHand.upgradeToAddedKan(added, meldIndex)
            }

            GameAction.KanType.OPEN_KAN -> null
        }
    }

    /** 評估者的手牌換成 [hand]、牌河加上 [discarded] 後的視角。 */
    private fun hypotheticalView(hand: Hand, discarded: IdentifiedTile?): HypotheticalView {
        val players = snapshot.players.map { player ->
            if (player.id != selfId) {
                player
            } else {
                player.copy(
                    hand = hand.toSnapshot(isVisible = true, revealsClosedKanTiles = true),
                    discardPile = discarded?.let { player.discardPile.discardTile(it) } ?: player.discardPile,
                )
            }
        }
        return HypotheticalView(
            id = nextViewId++,
            view = PositionView(snapshot = snapshot.copy(players = players), evaluatorId = selfId),
        )
    }

    /** 依資訊範圍列入防守計算的對手。 */
    private fun threats(): List<OpponentThreat> {
        if (level.defenseScope == DefenseScope.NONE) return emptyList()
        return snapshot.players
            .filter { it.id != selfId && it.id !in snapshot.finishedPlayerIds }
            .mapNotNull { opponent ->
                val threat = opponentModel.threat(currentView, opponent.id)
                when (level.defenseScope) {
                    DefenseScope.NONE -> null
                    DefenseScope.HIGH_THREAT_ONLY -> if (threat.readyProbability < parameters.highThreatReadyProbability) {
                        null
                    } else {
                        OpponentThreat(
                            opponentId = opponent.id,
                            readyProbability = threat.readyProbability,
                            lossOnDealIn = placement.loss(opponentModel.baselineWinValue(currentView, opponent.id).toDouble()),
                        )
                    }
                    DefenseScope.ALL -> OpponentThreat(
                        opponentId = opponent.id,
                        readyProbability = threat.readyProbability,
                        lossOnDealIn = placement.loss(threat.expectedWinValue.toDouble()),
                    )
                }
            }
    }

    /** [ExpectedValueEvaluator] 的常數。 */
    private companion object {
        /** 兩個期望值視為相同的差距上限。 */
        const val EXPECTED_VALUE_TIE_TOLERANCE: Double = 1e-9
    }
}
