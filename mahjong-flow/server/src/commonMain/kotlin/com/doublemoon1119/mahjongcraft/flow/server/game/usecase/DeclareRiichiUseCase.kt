package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GamePresentationPublisher
import com.doublemoon1119.mahjongcraft.flow.common.game.service.toPresentation
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.service.GameSnapshotSynchronizer
import com.doublemoon1119.mahjongcraft.flow.server.game.service.HandSortPreferenceStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.SidewaysMarkedDiscardPile
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import org.koin.core.annotation.Factory
import org.koin.core.annotation.Provided
import kotlin.uuid.Uuid

/**
 * 宣告立直的實例化用例。
 *
 * 立直宣告本質上是「打出一張牌，同時把這張牌標記成立直宣告牌」，前置驗證（回合、是否已摸牌）
 * 與 [DiscardTileUseCase] 相同。額外驗證的部分刻意不在這裡重新實作聽牌/門前清/點數等規則，
 * 而是直接問該規則模組自己的 `LegalActionValidator`：現在 [GameAction.Riichi] 是不是合法動作。
 * 這樣一來，若某個規則根本沒有立直這個概念，`GameAction.Riichi` 本來就
 * 不會出現在合法動作清單中，這裡自然會回傳 [GameError.IllegalAction]，不需要額外判斷「這個
 * 規則支不支援立直」。
 *
 * 不過 `LegalActionValidator` 只確認「打出某一張牌後可以聽牌」，並不知道玩家實際選了哪張牌，
 * 所以這裡還要另外用向聽數計算器驗證：打出 [tileId] 這張特定的牌之後，手牌是否仍然聽牌。
 *
 * 立直宣告實際造成的狀態變化（哪些欄位要改、改成什麼樣子）完全交給 [MahjongRuleModule.declareRiichi] 處理——
 * 這裡刻意不轉型成任何規則專屬的具體型別（如 `RiichiPlayerState`），否則會綁死在單一實作上，
 * 之後若有其他規則也想支援類似立直的宣告機制、卻使用自己的一套狀態型別，這裡就會誤判失敗。
 *
 * 立直宣告牌打出後，其他玩家是否有資格吃/碰/槓/榮和這張牌（含一炮多響判定為流局的情況）交給
 * [DiscardReactionResolver] 處理，與 [DiscardTileUseCase] 共用同一套邏輯。沒有人可以反應時，
 * 還會額外檢查是否構成四家立直（[MahjongRuleModule.resolveSuuchaRiichi]）。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與向聽數計算器。
 * @property snapshotSynchronizer 對局快照同步服務。
 * @property handSortPreferenceStore 查詢玩家是否啟用自動整理手牌，見該類別 KDoc。
 * @property eventPublisher 對局通知服務。
 * @property presentationPublisher 對局呈現層通知服務，用於立直成立後通知平台呈現層更新立直棒。
 */
@Factory
class DeclareRiichiUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val snapshotSynchronizer: GameSnapshotSynchronizer,
    private val handSortPreferenceStore: HandSortPreferenceStore,
    @Provided private val eventPublisher: GameEventPublisher,
    @Provided private val presentationPublisher: GamePresentationPublisher,
) {
    /**
     * 執行立直宣告邏輯。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起立直宣告的玩家 Uuid。
     * @param tileId 欲作為立直宣告牌捨棄的唯一識別碼。
     * @return 宣告結果，成功時為 [Unit]，失敗時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid, tileId: Uuid): Outcome<Unit, GameError> {
        // 1. 以原子方式讀取桌況、驗證業務規則並寫回
        val outcome = gameRepository.update(gameId) { state ->
            when {
                state == null -> state to Outcome.Error(GameError.GameNotFound(gameId))
                state.players.none { it.id == playerId } ->
                    state to Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))

                state.currentPlayer.id != playerId ->
                    state to Outcome.Error(GameError.NotPlayersTurn(playerId, gameId))

                state.currentPlayer.hand.lastDrawn == null ->
                    state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Riichi))

                else -> {
                    val module = moduleRegistry.getModule(state.config)
                    val legalActions = module.createLegalActionValidator().getLegalActions(
                        tableState = state,
                        player = state.currentPlayer,
                        sourceAction = GameAction.Draw,
                        sourceDirection = RelativeDirection.Self,
                        incomingTile = null,
                    )
                    if (legalActions.none { it is GameAction.Riichi }) {
                        return@update state to Outcome.Error(
                            GameError.IllegalAction(
                                playerId,
                                gameId,
                                GameAction.Riichi,
                            ),
                        )
                    }

                    val discardResult = state.currentPlayer.hand.discardById(tileId)
                        ?: return@update state to Outcome.Error(
                            GameError.IllegalAction(
                                playerId,
                                gameId,
                                GameAction.Discard(tileId),
                            ),
                        )

                    val postDiscardHand = discardResult.hand
                    val shantenResult = module.createShantenCalculator()
                        .calculate(Hand(tiles = postDiscardHand.tiles, melds = postDiscardHand.melds))
                    if (shantenResult !is ShantenResult.Tenpai) {
                        return@update state to Outcome.Error(
                            GameError.IllegalAction(
                                playerId,
                                gameId,
                                GameAction.Riichi,
                            ),
                        )
                    }

                    // 立直宣告本質上也是一次捨牌，理應跟 DiscardTileUseCase 同樣尊重自動整理手牌偏好——
                    // 立直宣告牌若不是摸切（打的是手牌、剛摸到的牌因此併入手牌），併入的牌也要照
                    // tileOrder 排序，不能維持原始加入順序。
                    val organizedDiscardResult = if (handSortPreferenceStore.isEnabled(playerId)) {
                        discardResult.copy(hand = discardResult.hand.organize(module.tileOrder))
                    } else {
                        discardResult
                    }

                    // 這個規則不支援立直宣告時 declareRiichi 回傳 null。理論上不會走到這裡，
                    // 因為上面的 legalActions 檢查已經先擋下了；僅作防呆。
                    val declaration = module.declareRiichi(state, state.currentPlayer, organizedDiscardResult)
                        ?: return@update state to Outcome.Error(
                            GameError.IllegalAction(
                                playerId,
                                gameId,
                                GameAction.Riichi,
                            ),
                        )

                    val updatedPlayer = declaration.player
                        .recordAction(GameAction.Riichi)
                        .recordAction(GameAction.Discard(discardResult.tile.id))
                    val updatedPlayers = state.players.map { if (it.id == playerId) updatedPlayer else it }
                    val stateAfterDeclaration = state.copy(
                        players = updatedPlayers,
                        dynamicRuleState = declaration.dynamicRuleState,
                    )

                    val resolved = DiscardReactionResolver.resolve(
                        state,
                        stateAfterDeclaration,
                        module,
                        playerId,
                        discardResult.tile,
                    )

                    // 沒有觸發一炮多響流局、也沒有人可反應時，額外檢查是否構成四家立直。
                    val suuchaReason =
                        if (resolved.abortiveDrawReason == null && resolved.tableState.pendingReaction == null) {
                            module.resolveSuuchaRiichi(resolved.tableState)
                        } else {
                            null
                        }
                    val finalResult = if (suuchaReason != null) {
                        resolved.copy(
                            tableState = resolved.tableState.copy(
                                players = resolved.tableState.players.map {
                                    it.recordAction(
                                        GameAction.ExhaustiveDraw(
                                            suuchaReason,
                                        ),
                                    )
                                },
                            ),
                            abortiveDrawReason = suuchaReason,
                        )
                    } else {
                        resolved
                    }

                    finalResult.tableState to Outcome.Success(finalResult)
                }
            }
        }

        if (outcome is Outcome.Error) return outcome
        val result = (outcome as Outcome.Success).value
        val newState = result.tableState

        // 2. 同步快照給所有正在觀察的玩家
        snapshotSynchronizer.syncAll(gameId)

        // 3. 通知對局內的所有玩家：先廣播立直宣告，再廣播這張牌的捨牌事件，流局有觸發時接著廣播流局事件
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Riichi)
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Discard(tileId))
            result.abortiveDrawReason?.let { reason ->
                eventPublisher.publish(gameId, player.id, playerId, GameAction.ExhaustiveDraw(reason))
            }
        }

        // 4. 觸發平台呈現層：重新排列立牌列、並把立直宣告牌移到牌河（橫放標記）——立直宣告本質上也是
        // 一次捨牌，跟 [DiscardTileUseCase] 步驟 4 完全同一套呼叫。
        val module = moduleRegistry.getModule(newState.config)
        val seatIndex = newState.players.indexOfFirst { it.id == playerId }
        val discarder = newState.players[seatIndex]
        val dealerSeatIndex = newState.players.indexOfFirst { it.currentWind == Wind.EAST }
        presentationPublisher.publishPlayerAreaUpdated(
            gameId,
            seatIndex,
            discarder.hand.tiles.map { it.id },
            null,
            discarder.hand.melds.map { it.toPresentation(newState.config.revealsClosedKanTiles) },
            comboStickCount = if (seatIndex == dealerSeatIndex) newState.comboCount else 0,
        )
        presentationPublisher.publishDiscardPileUpdated(
            gameId,
            seatIndex,
            discarder.discardPile.entries.filterNot { it.isTaken }.map { it.tile.id },
            (discarder.discardPile as? SidewaysMarkedDiscardPile)?.sidewaysMarkedTileId(),
            newlyDiscardedTileId = tileId,
        )

        // 5. 通知平台呈現層更新立直棒——用規則無關的 isPlayerInRiichi hook 掃過全體玩家算出目前立直
        // 中的座位集合，不假設剛宣告的這位玩家一定在集合裡（雖然目前唯一支援立直的規則必然如此）。
        // 延續自前局的供託堆支數 = 場上目前總供託 - 這局自己宣告貢獻的支數，見 GamePresentationPublisher
        // KDoc；每次立直宣告固定貢獻剛好 1 支，公式恆成立。
        val riichiSeatIndices = newState.players.withIndex()
            .filter { (_, player) -> module.isPlayerInRiichi(player) }
            .map { (playerSeatIndex, _) -> playerSeatIndex }
            .toSet()
        val pooledStickCount = module.getStickPotCount(newState) - riichiSeatIndices.size
        presentationPublisher.publishRiichiSticksUpdated(gameId, riichiSeatIndices, dealerSeatIndex, newState.comboCount, pooledStickCount)

        // 6. 通知平台呈現層更新桌面局況顯示——立直宣告當下供託支數馬上 +1，若不在這裡也更新一次，
        // 顯示要等到下一次摸牌才會跟著變，體驗不一致。
        presentationPublisher.publishRoundInfoUpdated(gameId, module.getRoundInfoLines(newState))

        return Outcome.Success(Unit)
    }
}
