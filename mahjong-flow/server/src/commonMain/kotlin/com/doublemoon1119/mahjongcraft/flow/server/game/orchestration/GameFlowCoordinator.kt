package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.ContinuingWinSettlementDetail
import com.doublemoon1119.mahjongcraft.flow.common.game.model.Game
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.PendingGameTransition
import com.doublemoon1119.mahjongcraft.flow.common.game.model.RoundOutcomePresentationClassification
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinRoundDirective
import com.doublemoon1119.mahjongcraft.flow.common.game.model.newlyRecordedActionsByPlayerId
import com.doublemoon1119.mahjongcraft.flow.common.game.model.recordedDrawOutcome
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationBusyGate
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.WinPresentationRequest
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.ExpectedGameResult
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.ExpectedGameWrittenTwiceException
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.ExhaustiveDrawSettlementPresentationService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameAbortHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameDecisionAvailabilityService
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinPresentationHandoff
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementDetailResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.service.WinSettlementPresentationRequestFactory
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceAutomaticRoundPreparationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolvePostReactionRoundOutcomeUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ResolveWinRoundContinuationUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.ReturnToRoomUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.RoundCompletionSummary
import com.doublemoon1119.mahjongcraft.logic.table.RoundTransitionContext
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlinx.coroutines.sync.Mutex
import kotlin.uuid.Uuid

/**
 * 在 [GameActionRouter] 之上，自動銜接系統觸發 use case（[DeclareExhaustiveDrawUseCase]、
 * [AdvanceRoundUseCase]）的呼叫時機，讓呼叫端只需要送出玩家的
 * [GameCommand]，不需要自己判斷「這個結果是不是代表本局已經結束、該推進到下一局了」。
 *
 * 實際的外部整合層應該呼叫這裡，而不是直接呼叫 [GameActionRouter]——
 * 後者只做單純分派，不含這裡的自動銜接邏輯。
 *
 * 三種銜接時機：
 * 1. **最終反應後 outcome／一般流局**：任一命令的結果為 [GameError.WallExhausted] 時，先呼叫
 *    [resolvePostReactionRoundOutcomeUseCase]；沒有 extension outcome 成立才呼叫 [declareExhaustiveDrawUseCase]。
 * 2. **連莊/過莊**：任何造成本局結束的操作完成後，先將 [PendingGameTransition.AdvanceRound]
 *    寫入權威狀態；待呈現結束後，再由 [resumePendingGameTransition] 呼叫 [advanceRoundUseCase]。
 *    任一命令成功後，統一從權威桌況的 `actionHistory` 判斷是否已留下胡牌或流局記錄，不依命令型別
 *    猜測規則結果，因此第三方 extension command 也能沿用相同收斂流程。
 *
 * 正常命令拒絕保留原本結果。規則提供的能力無法履行，或後續流程出現契約矛盾時，
 * 中止該場並留下診斷，不以重試、跳過或虛構結算掩蓋失敗。
 *
 * 自動操作由 [driveAutomatedPlayers] 透過 [aiTurnDriver] 等驅動：讓所有輪到自己、或有資格回應且尚未回應的
 * AI 玩家依序自動行動，直到沒有任何 AI 需要行動為止——AI 背後沒有真人會主動送出命令，這一步
 * 讓加入房間的 AI 玩家真的能在牌局裡自動出手。它只供 [AutomatedAdvanceManager] 使用，確保同一局同一時間只有一個推進；
 * 其他呼叫端分派命令後向管理器請求推進。
 *
 * @property gameActionRouter 玩家發起命令的路由入口。
 * @property gameRepository 權威對局數據倉庫，用於判斷是否需要銜接。
 * @property moduleRegistry 麻將規則模組註冊中心。
 * @property winSettlementDetailResolverRegistry 特殊 win-equivalent outcome（例如流局滿貫）的胡牌
 *   詳情解析 registry，用於 [WinSettlementPresentationRequestFactory.createSpecialOutcome]。
 * @property declareExhaustiveDrawUseCase 一般流局結算用例。
 * @property resolvePostReactionRoundOutcomeUseCase 反應完成後解析規則專屬回合結果的用例。
 * @property resolveWinRoundContinuationUseCase 胡牌即時結算完成後，判定本局後續是否結束的用例；
 *   見 [ResolveWinRoundContinuationUseCase] KDoc。
 * @property advanceRoundUseCase 連莊/過莊/開下一局用例。
 * @property returnToRoomUseCase 對局結束後把場地轉回房間用例；[advanceRoundUseCase] 判定對局結束時
 *   會先持久化 [PendingGameTransition.ReturnToRoom]，再於同一次待完成流程收斂中呼叫此用例。
 * @property aiTurnDriver 找出下一個該行動的 AI 玩家與其命令。
 * @property forcedAutoPlayDriver 找出下一個必須由伺服器固定操作的真人玩家與命令。
 * @property automaticDecisionDriver 找出下一個由真人玩家本局自動設定立即執行的命令。
 * @property decisionAvailabilityService 在呈現忙碌時暫停玩家決策，閒置時恢復或調整計時並立即同步。
 * @property exhaustiveDrawSettlementPresentationService 建立並發布流局結算呈現資料的服務。
 * @property winPresentationHandoff 胡牌 use case 建構好的呈現內容交接點，見 [WinPresentationHandoff]。
 * @property presentationPublisher 發布不依賴特定平台的呈現請求。
 * @property presentationBusyGate 查詢平台是否仍在呈現需要等待的內容；[driveAutomatedPlayers] 與
 *   [resumePendingGameTransition] 都會在推進權威流程前檢查，忙碌時直接返回並留待後續心跳重試；
 *   實作由平台層提供，理由見 [GamePresentationBusyGate] KDoc。
 * @property roundPreparationAiDriver 解析下一筆自動開局準備提交；未提供時不處理 AI 準備提交。
 * @property advanceAutomaticRoundPreparationUseCase 收斂無須玩家輸入的開局準備步驟；未提供時略過。
 * @property abortGameUseCase 無法安全繼續時中止原場次並還原房間。
 * @property automaticCommandResolver 從同一份權威桌況取得安全代打命令。
 * @property failurePolicy 判斷流程中擴充失敗是否能隔離；取消與致命錯誤原樣外傳。
 */
class GameFlowCoordinator(
    private val gameActionRouter: GameActionRouter,
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val winSettlementDetailResolverRegistry: WinSettlementDetailResolverRegistry,
    private val declareExhaustiveDrawUseCase: DeclareExhaustiveDrawUseCase,
    private val resolvePostReactionRoundOutcomeUseCase: ResolvePostReactionRoundOutcomeUseCase,
    private val resolveWinRoundContinuationUseCase: ResolveWinRoundContinuationUseCase,
    private val advanceRoundUseCase: AdvanceRoundUseCase,
    private val returnToRoomUseCase: ReturnToRoomUseCase,
    private val aiTurnDriver: AiTurnDriver,
    private val forcedAutoPlayDriver: ForcedAutoPlayDriver,
    private val automaticDecisionDriver: AutomaticDecisionDriver? = null,
    private val decisionAvailabilityService: GameDecisionAvailabilityService,
    private val exhaustiveDrawSettlementPresentationService: ExhaustiveDrawSettlementPresentationService,
    private val winPresentationHandoff: WinPresentationHandoff,
    private val presentationPublisher: GamePresentationPublisher,
    private val presentationBusyGate: GamePresentationBusyGate,
    private val abortGameUseCase: GameAbortHandler,
    private val automaticCommandResolver: AutomaticGameCommandResolver,
    private val failurePolicy: ExtensionFailurePolicy = ExtensionFailurePolicy.Default,
    private val roundPreparationAiDriver: RoundPreparationAiDriver? = null,
    private val advanceAutomaticRoundPreparationUseCase: AdvanceAutomaticRoundPreparationUseCase? = null,
) {
    /** 避免玩家命令與恢復心跳同時重複執行同一個待完成流程。 */
    private val pendingTransitionMutex = Mutex()

    /**
     * 分派 [command] 並自動銜接對應的系統觸發 use case，**不會**接著驅動自動連鎖：呼叫端分派後須向
     * [AutomatedAdvanceManager] 請求推進，AI／強制自動操作玩家才會接著行動。被拒絕的手動命令（例如強制自動操作中的玩家）
     * 視為完全沒發生過，呼叫端可自行決定是否仍要請求推進。
     *
     * 已進入強制自動操作（[Game.forcedAutoPlayPlayerIds]）的玩家一律拒絕；自動操作透過
     * [submitAutomatedDecision] 提交，不經過此真人請求入口。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起操作的玩家 Uuid。
     * @param command 欲執行的操作。
     * @return 命令結果；規則契約錯誤導致中止時，回傳 [GameError.MatchAborted]。
     */
    suspend fun dispatch(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
    ): Outcome<Unit, GameError> {
        val game = gameRepository.getGame(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        if (!decisionAvailabilityService.reconcile(gameId)) {
            return Outcome.Error(GameError.UnsupportedAction(gameId, playerId, PRESENTATION_BUSY_REASON_ID))
        }
        if (playerId in game.forcedAutoPlayPlayerIds) {
            return Outcome.Error(GameError.ForcedAutoPlayActive(playerId, gameId))
        }
        return dispatchAndReconcile(gameId, playerId, command)
    }

    /**
     * 依序驅動強制自動操作、真人自動設定與 AI，直到目前沒有任何自動決策需要執行。
     *
     * 每次迭代前後比較權威狀態，沒有可執行操作或進展時便停止。本次推進只屬於原場次，
     * 同一場地已開新場時不繼續操作；超過 [MAX_ITERATIONS] 仍未收斂時中止原場次並記錄診斷。
     * 開局、逾時與定期檢查都經 [AutomatedAdvanceManager] 呼叫，沒有真人送出命令時仍能推進。
     *
     * 強制自動操作只鎖住逾時當下那一次決策。命令成功後才解除 [Game.forcedAutoPlayPlayerIds] 的標記，
     * 並校正下一次決策計時；失敗時不提前把操作權交還給玩家。
     *
     * 每次迭代開始前都會用 [presentationBusyGate] 確認這場對局目前沒有需要等待的呈現——不是只在呼叫
     * 這個函式之前檢查一次：連莊/過莊本身就可能在迴圈中途觸發新一局的開局呈現（[advanceRoundUseCase]
     * 銜接呼叫），如果只在最外層檢查一次，迴圈仍會在同一次呼叫裡繼續驅動新莊家的自動摸牌，讓新一局的
     * 開局呈現還沒結束，遊戲流程就已經搶跑；改成每次迭代都檢查，呈現開始後迴圈會在下一次迭代前
     * 自然停下，等下次心跳或玩家操作再重新呼叫。
     *
     * @param gameId 欲推進的遊戲。
     */
    internal suspend fun driveAutomatedPlayers(gameId: Uuid) {
        val matchId = currentMatchId(gameId) ?: return
        repeat(MAX_ITERATIONS) {
            if (currentMatchId(gameId) != matchId) return
            if (!advanceAutomatedPlayerStep(gameId)) return
            // 已排入下一段轉移或呈現忙碌時維持原本的交接節奏，留待後續恢復入口。
            if (gameRepository.getGame(gameId)?.pendingTransition != null || presentationBusyGate.isBusy(gameId)) return
        }
        val game = gameRepository.getGame(gameId)?.takeIf { it.matchId == matchId } ?: return
        abortGameUseCase.abort(game, GameAbortReasonIds.AUTOMATIC_DECISION_FAILED, "Automatic progression exceeded $MAX_ITERATIONS steps")
    }

    /**
     * 取得場地目前的場次身分，防止舊推進在返回房間後操作新場次。
     *
     * @param gameId 場地識別碼。
     * @return 目前場次識別碼；沒有對局時為 null。
     */
    internal suspend fun currentMatchId(gameId: Uuid): Uuid? = gameRepository.getGame(gameId)?.matchId

    /**
     * 推進一次自動操作鏈中的單一步驟。
     *
     * 單步會依序處理待完成流程、回合準備、強制自動操作、真人自動設定與 AI 決策；呈現忙碌、沒有
     * 可執行決策，或命令沒有改變權威 [Game] 時，會立即停止。此方法不會等待下一步，適合由外部
     * 逐次收集自動操作事件並以背壓控制推進速度；只經 [AutomatedAdvanceManager.runExclusive] 呼叫。
     *
     * @param gameId 欲推進的遊戲。
     * @return 權威 [Game] 或其待完成流程確實改變時為 `true`；沒有可推進的工作、呈現忙碌或命令
     *   未造成狀態變更時為 `false`。
     */
    internal suspend fun advanceAutomatedPlayerStep(gameId: Uuid): Boolean {
        val basis = gameRepository.getGame(gameId) ?: return false
        return try {
            advanceAutomatedPlayerStepUnchecked(gameId)
        } catch (failure: Throwable) {
            if (!failurePolicy.isIsolatable(failure)) throw failure
            abortAfterFailure(basis, failure)
            basis != gameRepository.getGame(gameId)
        }
    }

    /**
     * 執行一次已受失敗保護的推進；例外由外層依場次身分處理。
     *
     * @param gameId 要推進的場地。
     * @return 權威狀態是否前進。
     */
    private suspend fun advanceAutomatedPlayerStepUnchecked(gameId: Uuid): Boolean {
        if (!decisionAvailabilityService.reconcile(gameId)) return false
        val stepBefore = gameRepository.getGame(gameId) ?: return false

        if (resumePendingGameTransition(gameId)) {
            return stepBefore != gameRepository.getGame(gameId)
        }
        if (advanceAutomaticRoundPreparationUseCase?.invoke(gameId) == true) {
            return stepBefore != gameRepository.getGame(gameId)
        }

        val preparationAction = roundPreparationAiDriver?.resolveNextAction(gameId)
        if (preparationAction != null) {
            val result = submitAutomatedDecision(gameId, preparationAction.playerId, preparationAction.command, preparationAction.basis)
            if (preparationAction.clearsForcedAutoPlay && result is Outcome.Success) {
                clearForcedAutoPlay(gameId, preparationAction.playerId)
                decisionAvailabilityService.reconcile(gameId, preparationAction.playerId)
            }
            return stepBefore != gameRepository.getGame(gameId)
        }

        val forcedAction = forcedAutoPlayDriver.resolveNextAction(gameId)
        val automaticAction = if (forcedAction == null) automaticDecisionDriver?.resolveNextAction(gameId) else null
        val humanAction = forcedAction ?: automaticAction
        if (humanAction != null) {
            val (playerId, command) = humanAction
            val result = submitAutomatedDecision(gameId, playerId, command, stepBefore)
            if (forcedAction != null && result is Outcome.Success) {
                clearForcedAutoPlay(gameId, playerId)
                decisionAvailabilityService.reconcile(gameId, playerId)
            }
        } else {
            val decision = aiTurnDriver.resolveNextAction(gameId) ?: return false
            submitAutomatedDecision(gameId, decision.playerId, decision.command, decision.basis)
        }
        if (presentationBusyGate.isBusy(gameId)) {
            return stepBefore != gameRepository.getGame(gameId)
        }
        resumePendingGameTransition(gameId)
        return stepBefore != gameRepository.getGame(gameId)
    }

    /**
     * 收斂權威狀態中尚待完成的遊戲流程。
     *
     * [Game.pendingTransition] 會持久化，因此正常執行期間與 server 重啟後都可由平台心跳重複呼叫
     * 本方法：根據明確的 [PendingGameTransition] 補做 [advanceRoundUseCase] 或
     * [returnToRoomUseCase]，不從桌況或呈現實作反推權威意圖。
     *
     * 方法只在呈現已經閒置時收斂，不會自行掛起等待，適合每次心跳輪詢。回傳 `true`
     * 代表偵測到待完成的流程：已執行收斂，或因契約矛盾而中止該場。
     * 呼叫端這一輪不再驅動自動操作，不對無法執行的同一步無限重試。
     *
     * @param gameId 欲收斂待完成流程的對局。
     * @return 是否偵測到待完成的遊戲流程。
     */
    suspend fun resumePendingGameTransition(gameId: Uuid): Boolean {
        if (presentationBusyGate.isBusy(gameId)) return false
        // 中途胡牌的呈現不一定阻塞其他玩家繼續摸打（因此 driveAutomatedPlayers 刻意不查這一項），但本局要等
        // 它結束才真的換局，否則尚未呈現完的結算會被新一局的開局呈現取代。
        if (presentationBusyGate.isPresentingContinuingWin(gameId)) return false
        pendingTransitionMutex.lock()
        try {
            val basis = gameRepository.getGame(gameId) ?: return false
            val transition = basis.pendingTransition ?: return false
            guardProgress(basis) {
                val result = when (transition) {
                    PendingGameTransition.AdvanceRound -> {
                        when (val result = advanceRoundUseCase(gameId)) {
                            is Outcome.Success -> Outcome.Success(Unit)
                            is Outcome.Error -> result
                        }
                    }

                    PendingGameTransition.ReturnToRoom -> {
                        when (val result = returnToRoomUseCase(gameId)) {
                            is Outcome.Success -> Outcome.Success(Unit)
                            is Outcome.Error -> result
                        }
                    }
                }
                if (result is Outcome.Error && result.error !is GameError.GameNotFound) {
                    throw RuleContractViolationException(
                        reasonId = GameAbortReasonIds.INVALID_PROGRESSION,
                        diagnostic = "Pending transition $transition was rejected: ${result.error::class.simpleName}",
                    )
                }
                result
            }
            return true
        } finally {
            pendingTransitionMutex.unlock()
        }
    }

    /**
     * 無條件分派 [command]、做系統銜接，再調整決策計時器（見 [reconcileAfter]）。
     *
     * @param gameId 對局識別碼。
     * @param playerId 發出命令的玩家識別碼。
     * @param command 欲分派的命令。
     * @return 分派及後續計時調整後的結果。
     */
    private suspend fun dispatchAndReconcile(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
    ): Outcome<Unit, GameError> {
        val basis = gameRepository.getGame(gameId) ?: return Outcome.Error(GameError.GameNotFound(gameId))
        val result = guardProgress(basis) {
            dispatchAndChain(gameId, playerId, command) { gameActionRouter(gameId, playerId, command) }
        }
        return reconcileAfter(gameId, playerId, requireNotNull(result) { "An unconditional dispatch is never stale" })
    }

    /**
     * 送出依 [expectedGame] 決定的自動命令，只在權威遊戲仍是 [expectedGame] 時套用。
     *
     * 呈現忙碌時不送出。命令的那一次權威寫入在交易內比對 [expectedGame]（見 [GameRepository.withExpectedGame]）；
     * 不符時命令完全沒有執行，也不做系統銜接與計時調整，由下一輪推進重新判斷誰該行動。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 由策略或伺服器代操作的玩家。
     * @param command 自動決定的遊戲命令或開局準備提交。
     * @param expectedGame 決策所依據的權威遊戲。
     * @return 命令或一次合法後備的結果；過期、呈現忙碌時為 null；契約矛盾時為中止錯誤。
     */
    private suspend fun submitAutomatedDecision(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
        expectedGame: Game,
    ): Outcome<Unit, GameError>? {
        if (presentationBusyGate.isBusy(gameId)) return null
        if (gameRepository.getGame(gameId) != expectedGame) return null
        val result = guardProgress(expectedGame) {
            val first = dispatchExpectedCommand(gameId, playerId, command, expectedGame)
            if (first !is Outcome.Error || gameRepository.getGame(gameId) != expectedGame) return@guardProgress first
            val fallback = automaticCommandResolver.resolve(expectedGame.tableState, playerId)
            if (fallback == null || fallback == command || command is GameCommand.SubmitRoundPreparation) {
                throw RuleContractViolationException(
                    GameAbortReasonIds.AUTOMATIC_DECISION_FAILED,
                    "Automatic command ${command::class.simpleName} was rejected: ${first.error::class.simpleName}; no safe distinct fallback",
                )
            }
            val retried = dispatchExpectedCommand(gameId, playerId, fallback, expectedGame)
            if (retried is Outcome.Error && gameRepository.getGame(gameId) == expectedGame) {
                throw RuleContractViolationException(
                    GameAbortReasonIds.AUTOMATIC_DECISION_FAILED,
                    "Fallback ${fallback::class.simpleName} was rejected: ${retried.error::class.simpleName}",
                )
            }
            retried
        } ?: return null
        return reconcileAfter(gameId, playerId, result)
    }

    /**
     * 只為主要命令建立一次條件式寫入範圍，後備與系統銜接不共用舊範圍。
     *
     * @param gameId 場地識別碼。
     * @param playerId 行動者。
     * @param command 要提交的命令。
     * @param expectedGame 決策依據。
     * @return 套用結果；過期時為 null。
     */
    private suspend fun dispatchExpectedCommand(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
        expectedGame: Game,
    ): Outcome<Unit, GameError>? = dispatchAndChain(gameId, playerId, command) {
        val conditional = try {
            gameRepository.withExpectedGame(gameId, expectedGame) { gameActionRouter(gameId, playerId, command) }
        } catch (violation: ExpectedGameWrittenTwiceException) {
            throw RuleContractViolationException(
                GameAbortReasonIds.AUTOMATIC_DECISION_FAILED,
                "Automated command ${command::class.simpleName} by $playerId attempted a second authoritative write; first write may be committed: ${violation.message}",
            )
        }
        when (conditional) {
            is ExpectedGameResult.Applied -> conditional.value
            ExpectedGameResult.Stale -> null
        }
    }

    /**
     * 對同一場次的規則矛盾執行中止；正常拒絕與過期結果保持原語意。
     *
     * @param T 用例成功結果型別。
     * @param basis 這次操作開始時的場次。
     * @param block 要保護的命令或系統銜接。
     * @return 原結果，或已中止錯誤；過期仍為 null。
     */
    private suspend fun <T> guardProgress(basis: Game, block: suspend () -> Outcome<T, GameError>?): Outcome<T, GameError>? = try {
        val result = block()
        val progressionError = (result as? Outcome.Error)?.error as? GameError.InvalidMatchProgression
        if (progressionError != null) {
            abortAfterFailure(
                basis,
                RuleContractViolationException(GameAbortReasonIds.INVALID_PROGRESSION, progressionError.reason),
            )
        } else {
            result
        }
    } catch (failure: Throwable) {
        if (!failurePolicy.isIsolatable(failure)) throw failure
        abortAfterFailure(basis, failure)
    }

    /**
     * 只中止仍屬於原場次的最新已提交狀態，不回滾前一個成功步驟。
     *
     * @param basis 失敗操作所屬的原場次。
     * @param failure 原始診斷。
     * @return 中止錯誤；原場次已不存在時為找不到對局。
     */
    private suspend fun abortAfterFailure(basis: Game, failure: Throwable): Outcome.Error<GameError> {
        val current = gameRepository.getGame(basis.id)?.takeIf { it.matchId == basis.matchId }
            ?: return Outcome.Error(GameError.GameNotFound(basis.id))
        val reasonId = (failure as? RuleContractViolationException)?.reasonId ?: GameAbortReasonIds.RULE_EXECUTION_FAILED
        val diagnostic = "${moduleRegistry.getModule(current.tableState.config).id}: ${failure::class.simpleName}: ${failure.message.orEmpty()}"
        return if (abortGameUseCase.abort(current, reasonId, diagnostic)) {
            Outcome.Error(GameError.MatchAborted(basis.id, reasonId))
        } else {
            Outcome.Error(GameError.GameNotFound(basis.id))
        }
    }

    /**
     * 分派命令與系統銜接完成後，依最終權威桌況調整決策計時器。
     *
     * 成功命令會將 [playerId] 視為完成一次決策，結算舊 timer；失敗命令只進行狀態校正，不重設該玩家
     * 已存在的基本思考時間。機械摸牌成功前沒有 timer，但完成後仍會透過相同流程建立自己回合的新 timer。
     *
     * @param gameId 對局識別碼。
     * @param playerId 發出命令的玩家識別碼。
     * @param result 命令執行結果。
     * @return 原命令執行結果。
     */
    private suspend fun reconcileAfter(
        gameId: Uuid,
        playerId: Uuid,
        result: Outcome<Unit, GameError>,
    ): Outcome<Unit, GameError> {
        decisionAvailabilityService.reconcile(
            gameId = gameId,
            completedPlayerId = playerId.takeIf { result is Outcome.Success },
        )
        return result
    }

    /**
     * 把 [playerId] 從強制自動操作名單移除，讓他下一次決策恢復由自己（或再次逾時後重新進入名單）
     * 決定，而不是繼續被伺服器代打。
     *
     * @param gameId 對局識別碼。
     * @param playerId 要解除強制自動操作的玩家識別碼。
     */
    private suspend fun clearForcedAutoPlay(gameId: Uuid, playerId: Uuid) {
        gameRepository.updateGame(gameId) { game ->
            (game?.copy(forcedAutoPlayPlayerIds = game.forcedAutoPlayPlayerIds - playerId) ?: game) to Unit
        }
    }

    /**
     * 分派 [command] 並自動銜接對應的系統觸發 use case。抽出成獨立方法讓 [driveAutomatedPlayers] 能
     * 直接呼叫——AI 送出的命令也必須經過同一套系統銜接邏輯，不能繞過去。
     *
     * @param gameId 對局識別碼。
     * @param playerId 發出命令的玩家識別碼。
     * @param command 欲分派的命令。
     * @param dispatchCommand 實際分派 [command] 的方式；回傳 null 代表命令因過期而沒有執行，此時不做任何系統銜接。
     * @return 命令的執行結果；[dispatchCommand] 回傳 null 時為 null。
     */
    private suspend fun dispatchAndChain(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
        dispatchCommand: suspend () -> Outcome<Unit, GameError>?,
    ): Outcome<Unit, GameError>? {
        val game = gameRepository.getGame(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        if (game.pendingRoundPreparation != null && command !is GameCommand.SubmitRoundPreparation) {
            return Outcome.Error(GameError.UnsupportedAction(gameId, playerId))
        }
        val previousState = gameRepository.getTableState(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        val result = dispatchCommand() ?: return null

        if (result is Outcome.Error && result.error is GameError.WallExhausted) {
            val specialOutcome = resolvePostReactionRoundOutcomeUseCase(gameId)
            if (specialOutcome is Outcome.Error) {
                throw RuleContractViolationException(
                    GameAbortReasonIds.MISSING_DRAW_SETTLEMENT,
                    "Special outcome resolution failed after wall exhaustion: ${specialOutcome.error::class.simpleName}",
                )
            }
            if (specialOutcome is Outcome.Success && specialOutcome.value != null) {
                val resolved = requireNotNull(specialOutcome.value)
                if (resolved.presentationClassification == RoundOutcomePresentationClassification.WIN_EQUIVALENT) {
                    presentationPublisher.publishWinSettlement(
                        gameId,
                        WinSettlementPresentationRequestFactory.createSpecialOutcome(
                            previousState = previousState,
                            outcome = resolved,
                            module = moduleRegistry.getModule(previousState.config),
                            aiPlayerIds = gameRepository.getGame(gameId)?.aiPlayerIds.orEmpty(),
                            detailResolverRegistry = winSettlementDetailResolverRegistry,
                        ),
                    )
                }
                chainAdvanceRound(gameId)
                return result
            }
            val exhaustiveDraw = declareExhaustiveDrawUseCase(gameId)
            if (exhaustiveDraw is Outcome.Error) {
                throw RuleContractViolationException(
                    GameAbortReasonIds.MISSING_DRAW_SETTLEMENT,
                    "Exhaustive draw settlement failed: ${exhaustiveDraw.error::class.simpleName}",
                )
            }
            if (exhaustiveDraw is Outcome.Success) {
                val currentState = exhaustiveDraw.value.tableState
                val module = moduleRegistry.getModule(previousState.config)
                val settlement = exhaustiveDraw.value.settlement
                exhaustiveDrawSettlementPresentationService.publish(
                    gameId = gameId,
                    previousState = previousState,
                    currentState = currentState,
                    module = module,
                    aiPlayerIds = gameRepository.getGame(gameId)?.aiPlayerIds.orEmpty(),
                    reason = settlement.reason,
                    tenpaiPlayerIds = settlement.tenpaiPlayerIds.takeIf { settlement.stickPotCollectorPlayerIds.isEmpty() },
                    revealedHands = settlement.revealedHands,
                )
                chainAdvanceRound(gameId)
            }
            return result
        }

        if (result is Outcome.Success) {
            handleHandConclusionIfPresent(
                gameId = gameId,
                playerId = playerId,
                previousState = previousState,
            )
        }

        return result
    }

    /**
     * 持久化「待進入下一局」意圖後立即返回；真正的 [advanceRoundUseCase] 由平台心跳在呈現結束後
     * 透過 [resumePendingGameTransition] 收斂。三個呼叫點（一般流局、四槓散了、一般結束本局判斷）
     * 共用這個方法，正常遊玩與 server 重啟恢復走完全相同的路徑。
     *
     * @param gameId 欲排定下一局的對局識別碼。
     */
    private suspend fun chainAdvanceRound(gameId: Uuid) {
        gameRepository.updateGame(gameId) { game ->
            game?.copy(pendingTransition = PendingGameTransition.AdvanceRound) to Unit
        }
    }

    /**
     * 若這次狀態變更新增途中流局記錄，建立統一回合結算呈現。
     *
     * @param gameId 對局識別碼。
     * @param previousState 執行命令前的桌況，用於辨識新增加的流局記錄。
     */
    private suspend fun publishNewAbortiveDrawIfPresent(
        gameId: Uuid,
        previousState: TableState,
    ) {
        val currentState = gameRepository.getTableState(gameId) ?: return
        val previousReasons = previousState.players.flatMap { player ->
            player.actionHistory.filterIsInstance<GameAction.ExhaustiveDraw>().map { it.reason }
        }.toSet()
        val reason = currentState.players.asSequence()
            .flatMap { it.actionHistory.asSequence() }
            .filterIsInstance<GameAction.ExhaustiveDraw>()
            .map { it.reason }
            .firstOrNull { it !in previousReasons }
            ?: return
        val module = moduleRegistry.getModule(currentState.config)
        exhaustiveDrawSettlementPresentationService.publish(
            gameId = gameId,
            previousState = previousState,
            currentState = currentState,
            module = module,
            aiPlayerIds = gameRepository.getGame(gameId)?.aiPlayerIds.orEmpty(),
            reason = reason,
            tenpaiPlayerIds = null,
            // 公開名單與等待牌都取自產生流局時保存的第一次 resolver 結果；呈現階段不能重算。
            revealedHands = currentState.revealedHandSettlements,
        )
    }

    /**
     * 命令成功執行後，若這次新增了流局或胡牌記錄，銜接對應的後續流程。
     *
     * 兩者都必須依這次命令**新增**的記錄判斷（跟 [previousState] 逐位比對 `actionHistory` 長度），
     * 不能像過去那樣直接掃描目前完整的 `actionHistory`——一旦某次胡牌經
     * [resolveWinRoundContinuationUseCase] 判定為 [WinRoundDirective.ContinueRound]，本局會繼續
     * 累積更多 `Tsumo`／`Ron` 記錄而不结束，若仍用「完整歷史裡有沒有」判斷，會讓本局稍後任何一次
     * 成功命令都被舊記錄誤判成「本局已結束」，重複觸發銜接。
     *
     * 流局（[GameAction.ExhaustiveDraw]）不屬於本次胡牌延續擴充的範圍，一律維持既有行為立即結束
     * 本局；胡牌（[GameAction.Tsumo]／[GameAction.Ron]）才會詢問 [resolveWinRoundContinuationUseCase]，
     * 只有取得 [WinRoundDirective.EndRound]（含未註冊 resolver 的規則）才銜接 [chainAdvanceRound]——
     * [WinRoundDirective.ContinueRound] 的桌況變化已經由該用例原子套用完畢，這裡不需要再做任何事。
     *
     * @param gameId 對局識別碼。
     * @param playerId 本次命令的玩家識別碼。
     * @param previousState 執行命令前的桌況。
     */
    private suspend fun handleHandConclusionIfPresent(
        gameId: Uuid,
        playerId: Uuid,
        previousState: TableState,
    ) {
        val currentState = gameRepository.getTableState(gameId) ?: return
        val newlyRecordedActionsByPlayerId = newlyRecordedActionsByPlayerId(previousState, currentState)

        val hasNewExhaustiveDraw = newlyRecordedActionsByPlayerId.values.any { actions ->
            actions.any { it is GameAction.ExhaustiveDraw }
        }
        if (hasNewExhaustiveDraw) {
            ensureRecordedDrawCompletion(gameId, currentState, newlyRecordedActionsByPlayerId)
            publishNewAbortiveDrawIfPresent(gameId, previousState)
            chainAdvanceRound(gameId)
            return
        }

        val newWinnerPlayerIds = newlyRecordedActionsByPlayerId
            .filterValues { actions -> actions.any { it is GameAction.Tsumo || it is GameAction.Ron } }
            .keys
        if (newWinnerPlayerIds.isEmpty()) return

        // 無論後續判定成不成功都先取走交接內容，避免失敗時殘留到下一次胡牌；贏家不符的殘留值會被
        // WinPresentationHandoff.take 自行丟棄並回傳 null。
        val presentation = winPresentationHandoff.take(gameId, newWinnerPlayerIds)
        val result = resolveWinRoundContinuationUseCase(gameId, previousState, newWinnerPlayerIds)
        if (result is Outcome.Error) {
            throw RuleContractViolationException(
                GameAbortReasonIds.INVALID_PROGRESSION,
                "Win continuation failed: ${result.error::class.simpleName}",
            )
        }
        val directive = (result as? Outcome.Success)?.value

        if (presentation != null) {
            val continuing = directive as? WinRoundDirective.ContinueRound
            // 「結算提供多少資訊」由 settlementDetail 決定；「是否暫停其他玩家」由 roundContinues 與平台
            // 決定（見 WinPresentationRequest KDoc），兩者互不牽連。
            val detail = continuing?.settlementDetail ?: ContinuingWinSettlementDetail.WINNER_DETAILS
            presentationPublisher.publishWinPresentation(
                gameId,
                WinPresentationRequest(
                    winnerPlayerIds = presentation.winnerPlayerIds,
                    // 胡牌呈現在任何模式下都完整提供（見 ContinuingWinSettlementDetail KDoc）；模式
                    // 只決定結算提供的資訊範圍。
                    celebration = presentation.celebration,
                    settlement = presentation.settlement.copy(
                        includesWinnerDetails = detail == ContinuingWinSettlementDetail.WINNER_DETAILS,
                    ),
                    roundContinues = continuing != null,
                ),
            )
        }

        if (directive == null || directive is WinRoundDirective.EndRound) {
            chainAdvanceRound(gameId)
        }
    }

    /**
     * 為由反應 resolver 直接產生的途中流局補上權威摘要；正式流局 use case 已寫入摘要時不覆蓋。
     *
     * @param gameId 對局識別碼。
     * @param state 記錄流局後的桌況。
     * @param newActionsByPlayerId 本次命令新增的玩家動作。
     */
    private suspend fun ensureRecordedDrawCompletion(
        gameId: Uuid,
        state: TableState,
        newActionsByPlayerId: Map<Uuid, List<GameAction>>,
    ) {
        gameRepository.updateGame(gameId) { game ->
            if (game == null || game.roundCompletion != null) return@updateGame game to Unit
            val outcome = recordedDrawOutcome(state, newActionsByPlayerId)
                ?: throw RuleContractViolationException(GameAbortReasonIds.INVALID_PROGRESSION, "Draw actions have no recorded draw outcome")
            val module = moduleRegistry.getModule(state.config)
            val directive = module.resolveRoundTransition(
                RoundTransitionContext(
                    tableState = state,
                    outcomeId = outcome.outcomeId,
                    classification = outcome.classification,
                    beneficiaryPlayerIds = outcome.beneficiaryPlayerIds,
                    responsiblePlayerIds = outcome.responsiblePlayerIds,
                    exhaustiveDrawReason = outcome.reason,
                ),
            )
            game.copy(
                roundCompletion = RoundCompletionSummary(
                    outcomeId = outcome.outcomeId,
                    classification = outcome.classification,
                    beneficiaryPlayerIds = outcome.beneficiaryPlayerIds,
                    responsiblePlayerIds = outcome.responsiblePlayerIds,
                    transitionDirective = directive,
                    settledScoresByPlayerId = state.players.associate { it.id to it.score },
                ),
            ) to Unit
        }
    }

    private companion object {
        /** 命令因 blocking presentation 暫時不可執行時使用的穩定原因 ID。 */
        const val PRESENTATION_BUSY_REASON_ID = "mahjongcraft:presentation_busy"

        /** [driveAutomatedPlayers] 的最大迭代次數，避免收斂性 bug 讓自動操作鏈路無限跑下去。 */
        const val MAX_ITERATIONS = 5000
    }
}
