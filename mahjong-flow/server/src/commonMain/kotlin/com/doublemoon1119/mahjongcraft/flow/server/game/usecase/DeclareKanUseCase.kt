package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.logic.base.ExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueContextCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiLegalActionValidator
import com.doublemoon1119.mahjongcraft.logic.table.PendingChankanReaction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TileWallRevealable
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 宣告暗槓（[GameAction.KanType.CLOSED_KAN]）或加槓（[GameAction.KanType.ADDED_KAN]）的實例化用例。
 *
 * 是否合法完全交給該規則模組自己的 `LegalActionValidator`——這裡不重新實作暗槓/加槓的偵測邏輯
 * （含立直後暗槓「不能改變聽牌」的限制），理由與 [DeclareRiichiUseCase]、
 * [DeclareKyuushuKyuuhaiUseCase] 相同。合法性確認後，先檢查有沒有其他玩家可以搶槓
 * （[GameAction.Kan.type] 為 [GameAction.KanType.ADDED_KAN] 時常見；暗槓只有國士無雙能搶，見
 * [RiichiLegalActionValidator] 既有邏輯）：
 * 有人可以搶時開一次反應視窗（[TableState.pendingChankan]，副露暫緩套用，交給
 * [RespondToChankanUseCase] 解析）；沒人可以搶時直接套用（[KanDeclarationApplier]）。多位玩家同時
 * 可搶時（罕見），依 [MahjongRuleConfig.multiRonPolicy] 決定實際開放
 * 給誰，與一般捨牌榮和共用同一套判定（見 [DiscardReactionResolver]）；判定為途中流局時，這次
 * 加槓視為未成立，不開反應視窗、不套用副露。
 *
 * 套用副露後依序記錄 [GameAction.Kan] → 從死牌區（[TileWall.drawLast]）
 * 補摸嶺上牌並記錄 [GameAction.Draw]，讓 [RiichiHandValueContextCalculator] 既有的
 * 嶺上開花偵測邏輯（依賴 `actionHistory` 最後兩筆是否恰為 `[Kan, Draw]`）能真正被觸發。
 *
 * 不需要新增任何 [MahjongRuleModule] 規則鉤子——
 * 套用副露、補摸嶺上牌、清除全場一發皆為既有通用方法（`Hand.call`/`Hand.upgradeToAddedKan`/
 * `TileWall.drawLast`/`module.onMeldClaimed`）的組合。不涉及包牌（Pao）：加槓沿用
 * `Hand.upgradeToAddedKan` 保留的原碰副露來源方位，暗槓沒有鳴牌來源，兩者皆不構成包牌，不呼叫
 * `applyPaoLiabilityIfTriggered`。
 *
 * 明牌（[GameAction.KanType.OPEN_KAN]，反應別人的捨牌）不在本用例範圍內，走 [RespondToDiscardUseCase]。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局 in-process 呈現觸發器。
 */
@Factory
class DeclareKanUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行暗槓/加槓宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起宣告的玩家 Uuid。
     * @param kanType 欲宣告的槓牌種類（僅支援 [GameAction.KanType.CLOSED_KAN]／[GameAction.KanType.ADDED_KAN]）。
     * @param tileId 觸發槓牌的牌的唯一識別碼，必須等於玩家目前的 `hand.lastDrawn.id`。
     * @return 宣告結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        kanType: GameAction.KanType,
        tileId: Uuid,
    ): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))

                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))

                state.pendingChankan != null ->
                    state to Outcome.Error(
                        GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                    )

                else -> {
                    val incomingTile = state.currentPlayer.hand.lastDrawn?.takeIf { it.id == tileId }
                        ?: return@update state to Outcome.Error(
                            GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                        )
                    val module = moduleRegistry.getModule(state.config)

                    // LegalActionValidator 的既有慣例是傳入的手牌「不含胡牌張」，胡牌張只透過
                    // incomingTile 參數單獨傳入；剝離手法與 DeclareTsumoUseCase/
                    // DeclareKyuushuKyuuhaiUseCase 相同（見規劃紀錄：暗槓判定 closedKanCount 若不
                    // 剝離 lastDrawn 會多算一張）。
                    val playerForCheck =
                        state.currentPlayer.copy(hand = state.currentPlayer.hand.copy(lastDrawn = null))
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = playerForCheck,
                        sourceAction = GameAction.Draw,
                        sourceDirection = RelativeDirection.Self,
                        incomingTile = incomingTile,
                    )
                    val kanAction = legalActions.filterIsInstance<GameAction.Kan>().firstOrNull { it.type == kanType }
                        ?: return@update state to Outcome.Error(
                            GameError.IllegalAction(playerId, gameId, GameAction.Kan(kanType, tileId, emptyList())),
                        )

                    // 搶槓資格：這張牌尚未套用進副露，其他玩家只需要各自問一次 getLegalActions
                    // 就能判斷是否能榮和它，不需要先套用副露。「反應」分支不分辨 sourceAction 種類，
                    // 會一併算出 Pon/Chi/OpenKan 資格，但搶槓情境下這些都不合法，只看 Ron。
                    val ronEligiblePlayerIds = state.players
                        .filter { it.id != playerId }
                        .filter { candidate ->
                            module.createLegalActionValidator().getLegalActions(
                                tableState = state,
                                player = candidate,
                                sourceAction = kanAction,
                                sourceDirection = state.relativeDirectionOf(candidate.id, playerId),
                                incomingTile = incomingTile,
                            ).any { it is GameAction.Ron }
                        }
                        .map { it.id }
                        .toSet()

                    // 搶槓多響（罕見：多位玩家同時可搶同一次加槓）依 MultiRonPolicy 決定實際開放給誰，
                    // 跟一般捨牌榮和共用同一套判定（見 DiscardReactionResolver）——搶槓本質上就是
                    // 「榮和一張牌，只是牌的來源是加槓而非捨牌」，沒有理由用不同規則。
                    val ronResolution = when (ronEligiblePlayerIds.size) {
                        0, 1 -> null
                        2 -> state.config.multiRonPolicy.doubleRonResolution
                        else -> state.config.multiRonPolicy.tripleRonResolution
                    }
                    val ronWinningPlayerIds = when (ronResolution) {
                        null, RonResolution.ALL_WINNERS -> ronEligiblePlayerIds
                        RonResolution.NEAREST_WINNER -> setOf(
                            state.nearestPlayerInTurnOrder(
                                playerId,
                                ronEligiblePlayerIds,
                            ),
                        )

                        RonResolution.ABORTIVE_DRAW -> emptySet()
                    }
                    val abortiveDrawReason = if (ronResolution == RonResolution.ABORTIVE_DRAW) {
                        module.resolveMultiRonAbortiveDraw()
                    } else {
                        null
                    }

                    if (abortiveDrawReason != null) {
                        // 多響流局：這次加槓視為未成立（比照 RespondToChankanUseCase 既有的「搶槓
                        // 成功時，暗槓/加槓視為未成立」原則），不套用 KanDeclarationApplier、不補
                        // 嶺上牌，直接記錄流局。
                        val newState = state.copy(
                            players = state.players.map { it.recordAction(GameAction.ExhaustiveDraw(abortiveDrawReason)) },
                        )
                        return@update newState to Outcome.Success(
                            KanResult(
                                newState,
                                kanAction,
                                drawHappened = false,
                                abortiveDrawReason = abortiveDrawReason,
                            ),
                        )
                    }

                    if (ronWinningPlayerIds.isNotEmpty()) {
                        val newState = state.copy(
                            pendingChankan = PendingChankanReaction(
                                playerId,
                                kanAction,
                                incomingTile,
                                ronWinningPlayerIds,
                            ),
                        )
                        return@update newState to Outcome.Success(KanResult(newState, kanAction, drawHappened = false))
                    }

                    val applied = KanDeclarationApplier.apply(state, playerId, kanAction, incomingTile, module)
                    if (applied.rinshanTile == null) {
                        return@update state to Outcome.Error(GameError.WallExhausted(gameId))
                    }
                    applied.tableState to Outcome.Success(KanResult(applied.tableState, kanAction, drawHappened = true))
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 廣播槓牌宣告；若無人可搶槓、副露已直接套用，依序再廣播補摸嶺上牌事件；若搶槓多響
        //    依規則設定判定為流局，則改廣播流局事件（此時副露未套用，不會有補摸嶺上牌事件）
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, result.kanAction)
            if (result.drawHappened) {
                eventPublisher.publish(gameId, player.id, playerId, GameAction.Draw)
            }
            result.abortiveDrawReason?.let { reason ->
                eventPublisher.publish(gameId, player.id, playerId, GameAction.ExhaustiveDraw(reason))
            }
        }

        // 副露成立時（無人搶槓、也未判定為途中流局），重新呈現宣告者的整份手牌/摸牌位/副露——把補到
        // 的嶺上牌移到摸牌位，跟一般摸牌同一套呈現慣例（見 DrawTileUseCase），先前遺漏這一步會讓
        // 補到的嶺上牌在玩家端看起來像是憑空消失，只看到副露成立、看不到補牌動作。
        if (result.drawHappened) {
            val declarerSeatIndex = newState.players.indexOfFirst { it.id == playerId }
            val declarer = newState.players[declarerSeatIndex]
            val dealerSeatIndex = newState.players.indexOfFirst { it.currentWind == Wind.EAST }
            presentationPublisher.publishPlayerAreaUpdated(
                gameId,
                declarerSeatIndex,
                declarer.hand.tiles.map { it.id },
                declarer.hand.lastDrawn?.id,
                declarer.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
                comboStickCount = if (declarerSeatIndex == dealerSeatIndex) newState.comboCount else 0,
            )
            // 槓牌成立後可能翻開新的一張寶牌指示牌（例如日麻的槓寶牌）；不支援 TileWallRevealable
            // 的規則永遠算出空集合，呼叫這個方法沒有任何效果。
            (newState.dynamicRuleState as? TileWallRevealable)?.let { revealable ->
                presentationPublisher.publishDeadWallRevealUpdated(gameId, revealable.getVisibleTileIds(newState))
            }
        }

        return Outcome.Success(Unit)
    }

    /**
     * `update` 區塊內部使用的中繼結果，讓 [kanAction]/[drawHappened]/[abortiveDrawReason] 能跟著
     * [tableState] 一起帶出 `gameRepository.update` 的作用域，供廣播事件時使用。[drawHappened] 為
     * false 代表這次宣告開啟了搶槓反應視窗、或搶槓多響判定為流局，副露與嶺上摸牌皆尚未套用，不應
     * 廣播 [GameAction.Draw]。[abortiveDrawReason] 非 null 代表搶槓多響依規則設定判定為流局，這次
     * 加槓視為未成立。
     */
    private data class KanResult(
        val tableState: TableState,
        val kanAction: GameAction.Kan,
        val drawHappened: Boolean,
        val abortiveDrawReason: ExhaustiveDrawReason? = null,
    )
}
