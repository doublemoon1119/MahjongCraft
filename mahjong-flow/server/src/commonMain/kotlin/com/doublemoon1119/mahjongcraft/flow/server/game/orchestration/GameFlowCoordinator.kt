package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.AdvanceRoundUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareExhaustiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareSuukanNagareUseCase
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 在 [GameActionRouter] 之上，自動銜接 3 個系統觸發 use case（[DeclareExhaustiveDrawUseCase]、
 * [DeclareSuukanNagareUseCase]、[AdvanceRoundUseCase]）的呼叫時機，讓呼叫端只需要送出玩家的
 * [GameCommand]，不需要自己判斷「這個結果是不是代表本局已經結束、該推進到下一局了」。
 *
 * 未來真正的呼叫端（例如 Minecraft 平台層）應該呼叫這裡，而不是直接呼叫 [GameActionRouter]——
 * 後者只做單純分派，不含這裡的自動銜接邏輯。
 *
 * 三種銜接時機（詳見 `docs/temp/game-orchestration-design.md` 子項 3 的設計討論）：
 * 1. **一般流局**：任一命令的結果為 [GameError.WallExhausted] 時，立即呼叫
 *    [declareExhaustiveDrawUseCase]（不開任何等待窗口——`WallExhausted` 代表玩家根本沒摸到牌，
 *    不存在「先讓玩家自摸」的中間狀態）。
 * 2. **四槓散了**：[GameCommand.Discard]/[GameCommand.Riichi] 在送進
 *    [GameActionRouter]（進而是 [DiscardTileUseCase] 或
 *    `com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareRiichiUseCase`）之前，
 *    先問 `com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule.resolveSuukanNagare`——
 *    若成立，代表玩家選擇不嘗試嶺上開花（已經有機會呼叫 [GameCommand.Tsumo] 但沒有），直接改呼叫
 *    [declareSuukanNagareUseCase]，原本的捨牌/立直請求不會真的被套用。
 * 3. **連莊/過莊**：任何造成本局結束的操作完成後呼叫 [advanceRoundUseCase]。哪些命令「一定」
 *    結束本局（[GameCommand.Tsumo]/[GameCommand.KyuushuKyuuhai]，以及上方兩種系統銜接本身）
 *    由呼叫端結構性得知，不需要額外檢查；[GameCommand.Discard]/[GameCommand.Riichi]/
 *    [GameCommand.RespondToDiscard]/[GameCommand.RespondToChankan] 是否結束本局則要看內部
 *    分支結果（榮和、或內嵌的四風連打/四家立直/三家和了流局），這裡透過檢查桌上是否有任一玩家
 *    的 `actionHistory` 剛記錄了 `Tsumo`/`Ron`/`ExhaustiveDraw` 判斷（[GameCommand.Draw]/
 *    [GameCommand.Kan] 的一般成功路徑則不會結束本局，不檢查）。
 *
 * 銜接呼叫皆為 best-effort：若銜接呼叫本身失敗，不會覆蓋原始命令的執行結果——玩家自己那次操作
 * 是否成功，跟後續系統銜接是否成功，是兩件事。
 *
 * @property gameActionRouter 玩家發起命令的路由入口。
 * @property gameRepository 權威對局數據倉庫，用於判斷是否需要銜接。
 * @property moduleRegistry 麻將規則模組註冊中心，用於解析四槓散了判定。
 * @property declareExhaustiveDrawUseCase 一般流局結算用例。
 * @property declareSuukanNagareUseCase 四槓散了結算用例。
 * @property advanceRoundUseCase 連莊/過莊/開下一局用例。
 */
@Factory
class GameFlowCoordinator(
    private val gameActionRouter: GameActionRouter,
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    private val declareExhaustiveDrawUseCase: DeclareExhaustiveDrawUseCase,
    private val declareSuukanNagareUseCase: DeclareSuukanNagareUseCase,
    private val advanceRoundUseCase: AdvanceRoundUseCase,
) {
    /**
     * 分派 [command] 並自動銜接對應的系統觸發 use case。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起操作的玩家 Uuid。
     * @param command 欲執行的操作。
     * @return [GameActionRouter]（或四槓散了攔截時改呼叫的 [declareSuukanNagareUseCase]）的執行結果。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
    ): Outcome<Unit, GameError> {
        if (command is GameCommand.Discard || command is GameCommand.Riichi) {
            redirectToSuukanNagareIfPending(gameId, playerId)?.let { return it }
        }

        val result = gameActionRouter(gameId, playerId, command)

        if (result is Outcome.Error && result.error is GameError.WallExhausted) {
            if (declareExhaustiveDrawUseCase(gameId) is Outcome.Success) {
                advanceRoundUseCase(gameId)
            }
            return result
        }

        if (result is Outcome.Success && commandMayHaveEndedHand(command, gameId)) {
            advanceRoundUseCase(gameId)
        }

        return result
    }

    /**
     * 若 [playerId] 正輪到自己回合、沒有任何反應視窗開著、且四槓散了已成立，改呼叫
     * [declareSuukanNagareUseCase] 並嘗試銜接 [advanceRoundUseCase]，回傳這次改呼叫的結果；
     * 否則回傳 null，代表呼叫端應照原命令正常分派給 [gameActionRouter]。
     */
    private suspend fun redirectToSuukanNagareIfPending(gameId: Uuid, playerId: Uuid): Outcome<Unit, GameError>? {
        val state = gameRepository.getTableState(gameId) ?: return null
        if (state.currentPlayer.id != playerId) return null
        if (state.pendingReaction != null || state.pendingChankan != null) return null

        val module = moduleRegistry.getModule(state.config)
        if (module.resolveSuukanNagare(state) == null) return null

        val result = declareSuukanNagareUseCase(gameId)
        if (result is Outcome.Success) {
            advanceRoundUseCase(gameId)
        }
        return result
    }

    /**
     * [command] 成功執行後，是否可能已經結束本局，需要銜接 [advanceRoundUseCase]。
     */
    private suspend fun commandMayHaveEndedHand(command: GameCommand, gameId: Uuid): Boolean = when (command) {
        GameCommand.Tsumo, GameCommand.KyuushuKyuuhai -> true
        is GameCommand.Discard, is GameCommand.Riichi,
        is GameCommand.RespondToDiscard, is GameCommand.RespondToChankan,
        -> {
            val state = gameRepository.getTableState(gameId)
            state?.players?.any { player ->
                player.actionHistory.any { it is GameAction.Tsumo || it is GameAction.Ron || it is GameAction.ExhaustiveDraw }
            } == true
        }
        else -> false
    }
}
