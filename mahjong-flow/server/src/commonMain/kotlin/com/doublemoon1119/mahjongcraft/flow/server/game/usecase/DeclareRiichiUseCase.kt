package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.judgment.ShantenResult
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
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
 * 立直宣告實際造成的狀態變化（哪些欄位要改、改成什麼樣子）完全交給
 * [com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.declareRiichi] 處理——
 * 這裡刻意不轉型成任何規則專屬的具體型別（如 `RiichiPlayerState`），否則會綁死在單一實作上，
 * 之後若有其他規則也想支援類似立直的宣告機制、卻使用自己的一套狀態型別，這裡就會誤判失敗。
 *
 * 立直宣告牌打出後，其他玩家是否有資格吃/碰/槓/榮和這張牌（含一炮多響判定為流局的情況）交給
 * [DiscardReactionResolver] 處理，與 [DiscardTileUseCase] 共用同一套邏輯。沒有人可以反應時，
 * 還會額外檢查是否構成四家立直
 * （[com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.resolveSuuchaRiichi]）。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器與向聽數計算器。
 * @property gameSnapshotRepository 對局快照數據倉庫。
 * @property eventPublisher 對局通知服務。
 */
@Factory
class DeclareRiichiUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val gameSnapshotRepository: GameSnapshotRepository,
    @Provided private val eventPublisher: GameEventPublisher,
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
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Riichi))
                    }

                    val discardResult = state.currentPlayer.hand.discardById(tileId)
                        ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Discard(tileId)))

                    val postDiscardHand = discardResult.hand
                    val shantenResult = module.createShantenCalculator()
                        .calculate(Hand(tiles = postDiscardHand.tiles, melds = postDiscardHand.melds))
                    if (shantenResult !is ShantenResult.Tenpai) {
                        return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Riichi))
                    }

                    // 這個規則不支援立直宣告時 declareRiichi 回傳 null。理論上不會走到這裡，
                    // 因為上面的 legalActions 檢查已經先擋下了；僅作防呆。
                    val declaration = module.declareRiichi(state, state.currentPlayer, discardResult)
                        ?: return@update state to Outcome.Error(GameError.IllegalAction(playerId, gameId, GameAction.Riichi))

                    val updatedPlayer = declaration.player
                        .recordAction(GameAction.Riichi)
                        .recordAction(GameAction.Discard(discardResult.tile.id))
                    val updatedPlayers = state.players.map { if (it.id == playerId) updatedPlayer else it }
                    val stateAfterDeclaration = state.copy(
                        players = updatedPlayers,
                        dynamicRuleState = declaration.dynamicRuleState,
                    )

                    val resolved = DiscardReactionResolver.resolve(state, stateAfterDeclaration, module, playerId, discardResult.tile)

                    // 沒有觸發一炮多響流局、也沒有人可反應時，額外檢查是否構成四家立直。
                    val suuchaReason = if (resolved.abortiveDrawReason == null && resolved.tableState.pendingReaction == null) {
                        module.resolveSuuchaRiichi(resolved.tableState)
                    } else {
                        null
                    }
                    val finalResult = if (suuchaReason != null) {
                        resolved.copy(
                            tableState = resolved.tableState.copy(
                                players = resolved.tableState.players.map { it.recordAction(GameAction.ExhaustiveDraw(suuchaReason)) },
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
        val observers = gameSnapshotRepository.getAllObservers(gameId)
        observers.forEach { observerId ->
            gameSnapshotRepository.setSnapshot(observerId, newState.toSnapshot(observerId))
        }

        // 3. 通知對局內的所有玩家：先廣播立直宣告，再廣播這張牌的捨牌事件，流局有觸發時接著廣播流局事件
        newState.players.forEach { player ->
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Riichi)
            eventPublisher.publish(gameId, player.id, playerId, GameAction.Discard(tileId))
            result.abortiveDrawReason?.let { reason ->
                eventPublisher.publish(gameId, player.id, playerId, GameAction.ExhaustiveDraw(reason))
            }
        }

        return Outcome.Success(Unit)
    }
}
