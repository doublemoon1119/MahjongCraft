package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.di.createBuiltInWinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.SettledWinPresentation
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationWinner
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinCelebrationCueResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementPresentationRequestFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.service.createBuiltInWinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.PendingReaction
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 回應捨牌反應視窗（吃/碰/明槓/過）的實例化用例。
 *
 * 對應 [DiscardTileUseCase] 捨牌後開啟的 `TableState.pendingReaction`：每位有資格的玩家各自呼叫
 * 一次本用例記錄自己的回應，直到所有有資格的玩家都回應完畢，才會實際結算。結算時的優先權規則：
 * 榮和 > 碰/明槓 > 吃（碰/明槓則是同一張牌結構上不可能被兩人同時碰/槓，故不需要更細緻的同類型排序；
 * 榮和則可能同時有多位贏家——見 [DiscardTileUseCase] 依 `MultiRonPolicy` 決定的一炮多響資格判斷，
 * 本用例只單純結算「這次收到幾筆榮和回應」，不重複判斷資格與人數限制）；若所有人都選擇過牌，
 * 則單純推進到下一位玩家，行為與捨牌時「無人可反應」的情況相同。
 *
 * 榮和結算後手牌即結束：本用例刻意只改動分數、贏家的 `actionHistory`，以及供託相關的動態規則狀態，
 * `currentPlayerIndex` 維持原樣不動（不像碰/吃/槓那樣把回合交給得標玩家），連莊/過莊/開下一局等
 * 後續流程留給未來獨立的單位。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與規則特有邏輯。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌，見該類別 KDoc。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class RespondToDiscardUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
    private val winPresentationHandoff: WinPresentationHandoff,
    private val winCelebrationCueResolverRegistry: WinCelebrationCueResolverRegistry =
        createBuiltInWinCelebrationCueResolverRegistry(),
    private val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry =
        createBuiltInWinSettlementDetailResolverRegistry(),
) {
    /**
     * 執行捨牌反應回應邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起回應的玩家 Uuid。
     * @param action 欲執行的回應動作（[GameAction.Pass]、[GameAction.Chi]、[GameAction.Pon]，
     *               或 [GameAction.KanType.OPEN_KAN] 型態的 [GameAction.Kan]）。
     * @return 回應結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, action: GameAction): Outcome<Unit, GameError> {
        val outcome = gameRepository.update(gameId) { state ->
            val pendingReaction = state?.pendingReaction
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))
                pendingReaction == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId !in pendingReaction.eligiblePlayerIds ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                playerId in pendingReaction.responses ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                else -> {
                    val discarder = state.players.first { it.id == pendingReaction.discarderId }
                    val responder = state.players.first { it.id == playerId }
                    val discardedTile = discarder.discardPile.entries.first { it.tile.id == pendingReaction.tileId }.tile

                    val module = moduleRegistry.getModule(state.config)
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = responder,
                        sourceAction = GameAction.Discard(pendingReaction.tileId),
                        sourceDirection = state.relativeDirectionOf(playerId, pendingReaction.discarderId),
                        incomingTile = discardedTile,
                    )
                    if (action !in legalActions) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, action))
                    }

                    // 過水：放過的當下若原本可以碰或榮和，記錄下來——碰用於本巡過水碰，
                    // 榮和用於同巡振聽判定（見 MahjongPlayer.passedTilesInRound 的用途說明）
                    val responderAfterPassedTile = if (action == GameAction.Pass &&
                        legalActions.any { it is GameAction.Pon || it is GameAction.Ron }
                    ) {
                        responder.addPassedTile(module.createTileInterpretationPolicy().canonicalize(discardedTile.tile))
                    } else {
                        responder
                    }

                    // 立直後放過原本合法的榮和機會即永久振聽，見 MahjongRuleModule.onPlayerDeclinedWin
                    // KDoc；未立直時放過榮和只構成一般同巡振聽，交給 module 內部依 isRiichi 判斷是否要
                    // 真的設定永久旗標，這裡不需要重複檢查。
                    val updatedResponder = if (action == GameAction.Pass && legalActions.any { it is GameAction.Ron }) {
                        module.onPlayerDeclinedWin(responderAfterPassedTile)
                    } else {
                        responderAfterPassedTile
                    }
                    val playersAfterResponse = state.players.map { if (it.id == playerId) updatedResponder else it }
                    val newPendingReaction = pendingReaction.copy(responses = pendingReaction.responses + (playerId to action))

                    val result = if (!newPendingReaction.isComplete) {
                        RespondResult(state.copy(players = playersAfterResponse, pendingReaction = newPendingReaction))
                    } else {
                        resolvePendingReaction(state, playersAfterResponse, newPendingReaction, discardedTile, module)
                    }

                    result.tableState to Outcome.Success(result)
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        snapshotSynchronizer.syncAll(gameId)

        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, action)
            if (result.rinshanDrawHappened) {
                eventPublisher.publish(gameId, player.id, playerId, GameAction.Draw)
            }
        }

        // 觸發平台呈現層：碰/吃/明槓得標時，丟牌者的 discardPile 被 takeLast() 標記，即使沒有新增
        // 捨牌，側身標記也可能因此位移，需要重新呈現這位丟牌者的牌河
        result.discarderId?.let { discarderId ->
            val discarderSeatIndex = newState.players.indexOfFirst { it.id == discarderId }
            val discarder = newState.players[discarderSeatIndex]
            presentationPublisher.publishDiscardPileUpdated(
                gameId,
                discarderSeatIndex,
                discarder.discardPile.entries.filterNot { it.isTaken }.map { it.tile.id },
                (discarder.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            )
        }

        // 碰/吃/明槓得標時，得標玩家的副露多了一組、手牌也少了對應張數，重新呈現整份手牌/摸牌位/
        // 副露；明槓另外補到嶺上牌時（[RespondResult.rinshanDrawHappened]）摸牌位自然帶著呈現，不需要
        // 額外判斷——合併呼叫後也順便補上先前缺漏的手牌重新呈現（吃/碰/明槓後手牌張數變少，先前完全
        // 沒有重新呈現手牌列）。
        result.winnerId?.let { winnerId ->
            result.resolvedAction?.let { resolvedAction ->
                presentationPublisher.publishGameActionSound(gameId, winnerId, resolvedAction)
            }
            val winnerSeatIndex = newState.players.indexOfFirst { it.id == winnerId }
            val winner = newState.players[winnerSeatIndex]
            val dealerSeatIndex = newState.dealerIndex
            presentationPublisher.publishPlayerAreaUpdated(
                gameId,
                winnerSeatIndex,
                winner.hand.tiles.map { it.id },
                winner.hand.lastDrawn?.id,
                winner.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
                comboStickCount = if (winnerSeatIndex == dealerSeatIndex) newState.comboCount else 0,
                // 吃/碰/明槓永遠整組一次成立新副露（附加到 exposedMelds 尾端，不是原地修改既有組），
                // 最後一組必定就是這次剛成立的那組，組內全部牌都該播放鳴牌動畫。
                animatedMeldClaimTileIds = winner.hand.melds.last().tiles.map { it.id }.toSet(),
            )
            // 明槓得標可能翻開新的一張寶牌指示牌，理由同 DeclareKanUseCase；吃/碰不構成槓，不需要
            // 檢查——只看剛成立的那組副露（永遠是 melds 的最後一組）是不是明槓。
            if (result.newlyRevealedDeadWallTileIds.isNotEmpty()) {
                presentationPublisher.publishDeadWallRevealUpdated(gameId, result.newlyRevealedDeadWallTileIds)
            }
        }

        // 建構胡牌演出內容並寫進交接槽——一炮多響時 result.ronWinnerIds 可能不只一人，打包成同一筆；
        // winningTileId 對每位贏家來說都是同一張放銃的捨牌。刻意不直接發布，理由同 DeclareTsumoUseCase。
        result.ronWinningTileId?.let { winningTileId ->
            result.ronWinnerIds.forEach { winnerId ->
                presentationPublisher.publishGameActionSound(gameId, winnerId, GameAction.Ron(winningTileId))
            }
            val module = moduleRegistry.getModule(newState.config)
            val presentation = SettledWinPresentation(
                winnerPlayerIds = result.ronWinnerIds,
                celebration = WinCelebrationRequest(
                    winningTileId,
                    isTsumo = false,
                    winners = result.ronWinnerIds.map { winnerId ->
                        WinCelebrationWinner(
                            newState.players.indexOfFirst { it.id == winnerId },
                            result.ronResolutions[winnerId]?.let {
                                winCelebrationCueResolverRegistry.resolve(result.ruleModuleId.orEmpty(), it.handValueResult)
                            },
                        )
                    },
                ),
                settlement = WinSettlementPresentationRequestFactory.create(
                    previousState = checkNotNull(result.previousTableState),
                    currentState = newState,
                    module = module,
                    outcomeId = BuiltInRoundOutcomeIds.RON,
                    isTsumo = false,
                    winningTileId = winningTileId,
                    responsiblePlayerId = result.ronDiscarderId,
                    resolutions = result.ronResolutions,
                    detailResolverRegistry = winSettlementDetailResolverRegistry,
                ),
            )
            winPresentationHandoff.stage(gameId, presentation)
        }

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [rinshanDrawHappened]／[discarderId]／[winnerId] 能跟著
     * [tableState] 一起帶出 `gameRepository.update` 的作用域，供廣播事件／觸發呈現時使用。
     * [rinshanDrawHappened] 為 true 代表明槓得標後成功補到嶺上牌，需要額外廣播 [GameAction.Draw]。
     * [discarderId] 只在碰/吃/明槓得標、實際對丟牌者的 `discardPile` 呼叫過 `takeLast()` 時才有值
     * （全員過牌與榮和都不會呼叫 `takeLast()`），供呼叫端重新呈現該玩家的牌河。[winnerId] 跟
     * [discarderId] 同一個有效視窗（碰/吃/明槓得標時才有值），供呼叫端重新呈現得標玩家的副露。
     * [ronWinnerIds] 只在榮和結算成立時非空（一炮多響可能不只一人），[ronWinningTileId] 是被榮和的那張
     * 捨牌，供呼叫端逐一觸發胡牌慶祝演出；跟 [winnerId]（碰/吃/明槓得標）是互斥的兩個視窗，同一次結算
     * 只會有其中一種非空。
     */
    private data class RespondResult(
        val tableState: TableState,
        val rinshanDrawHappened: Boolean = false,
        val discarderId: Uuid? = null,
        val winnerId: Uuid? = null,
        val resolvedAction: GameAction? = null,
        val ronWinnerIds: Set<Uuid> = emptySet(),
        val ronWinningTileId: Uuid? = null,
        val ronResolutions: Map<Uuid, com.doublemoon1119.mahjongcraft.logic.module.WinResolutionResult> = emptyMap(),
        val ruleModuleId: String? = null,
        val previousTableState: TableState? = null,
        val ronDiscarderId: Uuid? = null,
        val newlyRevealedDeadWallTileIds: Set<Uuid> = emptySet(),
    )

    /**
     * 所有有資格的玩家皆已回應，進行結算：找出優先權最高的非過牌回應套用鳴牌，或在全員過牌時單純推進回合。
     */
    private fun resolvePendingReaction(
        state: TableState,
        players: List<MahjongPlayer>,
        pendingReaction: PendingReaction,
        discardedTile: IdentifiedTile,
        module: MahjongRuleModule<*>,
    ): RespondResult {
        val ronWinnerIds = pendingReaction.responses.filterValues { it is GameAction.Ron }.keys
        if (ronWinnerIds.isNotEmpty()) {
            val resolved = RonSettlementResolver.resolve(
                state = state,
                players = players,
                discarderId = pendingReaction.discarderId,
                winningTile = discardedTile,
                module = module,
                winnerIds = ronWinnerIds,
            )
            return RespondResult(
                tableState = resolved?.tableState?.copy(pendingReaction = null)
                    ?: state.copy(players = players, pendingReaction = pendingReaction),
                ronWinnerIds = if (resolved != null) ronWinnerIds else emptySet(),
                ronWinningTileId = if (resolved != null) discardedTile.id else null,
                ronResolutions = resolved?.resolutions.orEmpty(),
                ruleModuleId = if (resolved != null) module.id else null,
                previousTableState = if (resolved != null) state.copy(players = players) else null,
                ronDiscarderId = if (resolved != null) pendingReaction.discarderId else null,
            )
        }

        val winningEntry = pendingReaction.responses.entries.firstOrNull { it.value is GameAction.Pon || it.value is GameAction.Kan }
            ?: pendingReaction.responses.entries.firstOrNull { it.value is GameAction.Chi }

        if (winningEntry == null) {
            // 所有人皆過牌：行為與捨牌時「無人可反應」相同，直接推進到下一位仍在本局中的玩家
            val stateWithPlayers = state.copy(players = players)
            val nextPlayer = stateWithPlayers.nextActivePlayerAfter(pendingReaction.discarderId)
            return RespondResult(
                stateWithPlayers.copy(
                    currentPlayerIndex = players.indexOf(nextPlayer),
                    pendingReaction = null,
                ),
            )
        }

        val winnerId = winningEntry.key
        val winnerAction = winningEntry.value
        val winner = players.first { it.id == winnerId }
        val winnerDirection = state.relativeDirectionOf(winnerId, pendingReaction.discarderId)
        val tileInterpretation = module.createTileInterpretationPolicy()

        val meldType = when (winnerAction) {
            is GameAction.Chi -> MeldType.CHI
            is GameAction.Pon -> MeldType.PON
            is GameAction.Kan -> MeldType.OPEN_KAN
            else -> error("Unreachable: only Chi/Pon/Kan can win a discard reaction")
        }
        val handTilesUsed: List<IdentifiedTile> = when (winnerAction) {
            is GameAction.Chi -> winnerAction.withTiles.mapNotNull { id -> winner.hand.standingTiles.find { it.id == id } }
            is GameAction.Kan -> winnerAction.withTiles.mapNotNull { id -> winner.hand.standingTiles.find { it.id == id } }
            is GameAction.Pon ->
                winner.hand.standingTiles
                    .filter {
                        tileInterpretation.canonicalize(it.tile) == tileInterpretation.canonicalize(discardedTile.tile)
                    }
                    .take(2)
        }

        val winnerWithPao = if (meldType == MeldType.PON || meldType == MeldType.OPEN_KAN) {
            module.applyPaoLiabilityIfTriggered(winner, discardedTile, winnerDirection)
        } else {
            winner
        }
        val calledHand = winnerWithPao.hand.call(meldType, handTilesUsed + discardedTile, discardedTile, winnerDirection)
        // 明槓得標後緊接著補摸嶺上牌（見下方 lastDrawn 賦值），這裡先不整理——理由同 DeclareKanUseCase
        // 沒有整理手牌時機點的說明：整理只在 lastDrawn == null（沒有還沒決定的摸牌）時才適用。
        val organizedHand = if (meldType != MeldType.OPEN_KAN && handSortPreferenceStore.isEnabled(winnerId)) {
            calledHand.organize(module.tileOrder)
        } else {
            calledHand
        }
        val winnerAfterMeld = winnerWithPao.copy(hand = organizedHand).recordAction(winnerAction)

        val playersAfterMeld = players.map { player ->
            when (player.id) {
                winnerId -> winnerAfterMeld
                pendingReaction.discarderId -> player.copy(discardPile = player.discardPile.takeLast())
                else -> player
            }
        }
        val playersAfterMeldClaimed = module.onMeldClaimed(playersAfterMeld)
        val winnerIndex = playersAfterMeldClaimed.indexOfFirst { it.id == winnerId }

        if (meldType != MeldType.OPEN_KAN) {
            return RespondResult(
                tableState = state.copy(
                    players = playersAfterMeldClaimed,
                    currentPlayerIndex = winnerIndex,
                    pendingReaction = null,
                ),
                discarderId = pendingReaction.discarderId,
                winnerId = winnerId,
                resolvedAction = winnerAction,
            )
        }

        // 明槓比照暗槓/加槓，得標後立即從死牌區補摸嶺上牌（KanDeclarationApplier.drawRinshanTile，
        // 共用同一份「王牌區前段保留給嶺上摸牌」的邏輯），取代原本依賴得標玩家事後另外呼叫
        // DrawTileUseCase（那是從牌山前端摸牌，摸錯位置）的既有錯誤行為。若王牌區的嶺上摸牌保留區
        // 恰好在此刻摸盡（極端邊界情況，理論上四槓散了流局應該已經先成立），這裡沒有 Outcome 通道
        // 可回報錯誤（本方法回傳單純 TableState），已知簡化：讓 lastDrawn 維持空，不中斷已經套用
        // 完成的副露結果。
        val rinshanTile = KanDeclarationApplier.drawRinshanTile(state)
        val playersWithRinshanDraw = if (rinshanTile == null) {
            playersAfterMeldClaimed
        } else {
            playersAfterMeldClaimed.map { player ->
                if (player.id == winnerId) {
                    player.copy(hand = player.hand.copy(lastDrawn = rinshanTile)).clearPassedTiles().recordAction(GameAction.Draw)
                } else {
                    player
                }
            }
        }

        return RespondResult(
            tableState = state.copy(
                players = playersWithRinshanDraw,
                currentPlayerIndex = winnerIndex,
                pendingReaction = null,
            ),
            rinshanDrawHappened = rinshanTile != null,
            discarderId = pendingReaction.discarderId,
            winnerId = winnerId,
            resolvedAction = winnerAction,
            newlyRevealedDeadWallTileIds = newlyRevealedDeadWallTileIds(
                state,
                state.copy(
                    players = playersWithRinshanDraw,
                    currentPlayerIndex = winnerIndex,
                    pendingReaction = null,
                ),
            ),
        )
    }
}
