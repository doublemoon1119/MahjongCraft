package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 查詢指定玩家目前合法動作清單的應用層用例。
 *
 * 純查詢、無副作用，包一層 `module.createLegalActionValidator().getLegalActions(...)`，讓
 * `:mahjong-flow` 呼叫端（例如未來的 Minecraft GUI）取得目前玩家在當前情境下的合法動作清單，
 * 不需要直接依賴 `:mahjong-logic` 的規則型別自行判斷。
 *
 * 呼叫端只給 [gameId]/[playerId]，這裡會依 [TableState]
 * 現況自動判斷屬於以下哪一種情境，並組出正確的 `sourceAction`/`sourceDirection`/`incomingTile` 參數：
 *
 * 1. 有資格搶槓、且尚未回應（`pendingChankan` 非 null）：比照 [RespondToChankanUseCase] 的既有慣例，
 *    過濾只留 [GameAction.Ron]/[GameAction.Pass]（`getLegalActions` 的「反應」分支不分辨
 *    `sourceAction` 種類，會一併算出吃/碰/明槓資格，這裡都不合法）。
 * 2. 有資格回應捨牌、且尚未回應（`pendingReaction` 非 null）：不過濾，Chi/Pon/Kan/Ron/Pass 皆可能合法。
 * 3. 輪到自己回合、且已經摸牌：`RiichiLegalActionValidator.getLegalActions` 對「自己回合」情境
 *    需要呼叫兩次才能拿到完整清單——一次 `incomingTile = null`（未剝離 `lastDrawn` 的原始手牌，
 *    只檢查 [GameAction.Riichi] 資格）、一次 `incomingTile = 剝離後的 lastDrawn`（檢查
 *    Tsumo/Kan/KyuushuKyuuhai 等資格），兩次結果需要合併，缺一次會漏掉對應的合法動作。
 * 4. 以上皆非：回傳空清單（不是錯誤——單純這個玩家現在沒有除了被動等待以外的事可做）。
 *
 * 回傳清單只包含「除了預設回合動作以外」的額外合法動作——例如自己回合已摸牌時，「捨牌」本身
 * 永遠不會出現在清單裡（`Validator` 既有慣例是把它當成永遠可用的預設動作），空清單不代表
 * 「什麼都不能做，包含不能捨牌」。
 *
 * @property gameRepository 權威對局數據倉庫。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析當前對局的合法動作判定器。
 */
@Factory
class GetLegalActionsUseCase(
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
) {
    /**
     * 查詢指定玩家目前的合法動作清單。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 欲查詢的玩家 Uuid。
     * @return 合法動作清單，找不到對局或玩家時為 [GameError]。
     */
    suspend operator fun invoke(gameId: Uuid, playerId: Uuid): Outcome<List<GameAction>, GameError> {
        val state = gameRepository.getTableState(gameId)
            ?: return Outcome.Error(GameError.GameNotFound(gameId))
        val player = state.players.firstOrNull { it.id == playerId }
            ?: return Outcome.Error(GameError.PlayerNotInGame(playerId, gameId))

        val module = moduleRegistry.getModule(state.config)
        val validator = module.createLegalActionValidator()
        val pendingChankan = state.pendingChankan
        val pendingReaction = state.pendingReaction

        val actions = when {
            pendingChankan != null &&
                playerId in pendingChankan.eligiblePlayerIds &&
                playerId !in pendingChankan.responses -> {
                validator.getLegalActions(
                    tableState = state,
                    player = player,
                    sourceAction = pendingChankan.kanAction,
                    sourceDirection = state.relativeDirectionOf(playerId, pendingChankan.declarerId),
                    incomingTile = pendingChankan.robbedTile,
                ).filter { it is GameAction.Ron || it == GameAction.Pass }
            }

            pendingReaction != null &&
                playerId in pendingReaction.eligiblePlayerIds &&
                playerId !in pendingReaction.responses -> {
                val discarder = state.players.first { it.id == pendingReaction.discarderId }
                val discardedTile = discarder.discardPile.entries.first { it.tile.id == pendingReaction.tileId }.tile
                validator.getLegalActions(
                    tableState = state,
                    player = player,
                    sourceAction = GameAction.Discard(pendingReaction.tileId),
                    sourceDirection = state.relativeDirectionOf(playerId, pendingReaction.discarderId),
                    incomingTile = discardedTile,
                )
            }

            state.currentPlayer.id == playerId &&
                player.hand.lastDrawn != null &&
                pendingChankan == null &&
                pendingReaction == null -> {
                val lastDrawn = player.hand.lastDrawn
                val riichiCheck = validator.getLegalActions(
                    tableState = state,
                    player = player,
                    sourceAction = GameAction.Draw,
                    sourceDirection = RelativeDirection.Self,
                    incomingTile = null,
                )
                val playerForCheck = player.copy(hand = player.hand.copy(lastDrawn = null))
                val otherChecks = validator.getLegalActions(
                    tableState = state,
                    player = playerForCheck,
                    sourceAction = GameAction.Draw,
                    sourceDirection = RelativeDirection.Self,
                    incomingTile = lastDrawn,
                )
                riichiCheck + otherChecks
            }

            else -> emptyList()
        }

        return Outcome.Success(actions)
    }
}
