package com.doublemoon1119.mahjongcraft.flow.server.game.orchestration

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameCommand
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareAbortiveDrawUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareKanUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DeclareTsumoUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DiscardTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.DrawTileUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToDiscardUseCase
import com.doublemoon1119.mahjongcraft.flow.server.game.usecase.RespondToKanUseCase
import org.koin.core.annotation.Factory
import kotlin.uuid.Uuid

/**
 * 把「玩家想執行的動作」（[GameCommand]）路由到對應的玩家發起 Game Use Case。
 *
 * 純分派，不含任何業務邏輯——每個 use case 自己的驗證、狀態套用、快照與事件同步皆維持不變，
 * 這裡只負責把 [gameId]/[playerId]/`command` 轉呼叫成正確的 use case 呼叫。讓未來的呼叫端
 * （例如 Minecraft 平台層的網路封包處理）只需要「解析請求 → 組出一個 [GameCommand] → 呼叫本路由」，
 * 不需要自己維護一份「哪個操作對應哪個 use case」的對照表。
 *
 * 系統觸發的 3 個 use case（`DeclareExhaustiveDrawUseCase`、`DeclareSuukanNagareUseCase`、
 * `AdvanceRoundUseCase`）不在這裡——它們沒有 `playerId`，呼叫時機由 [GameFlowCoordinator] 決定。
 *
 * @property drawTileUseCase 摸牌用例。
 * @property discardTileUseCase 捨牌用例。
 * @property declareTsumoUseCase 自摸宣告用例。
 * @property declareKanUseCase 暗槓/加槓宣告用例。
 * @property respondToDiscardUseCase 回應捨牌反應視窗用例。
 * @property respondToKanUseCase 回應搶槓反應視窗用例。
 * @property declareAbortiveDrawUseCase 通用途中流局宣告用例。
 * @property extensionCommandRegistry 規則擴充命令 handler registry。
 */
@Factory
class GameActionRouter(
    private val drawTileUseCase: DrawTileUseCase,
    private val discardTileUseCase: DiscardTileUseCase,
    private val declareTsumoUseCase: DeclareTsumoUseCase,
    private val declareKanUseCase: DeclareKanUseCase,
    private val respondToDiscardUseCase: RespondToDiscardUseCase,
    private val respondToKanUseCase: RespondToKanUseCase,
    private val declareAbortiveDrawUseCase: DeclareAbortiveDrawUseCase,
    private val extensionCommandRegistry: ExtensionGameCommandExecutorRegistry,
) {
    /**
     * 分派 [command] 到對應的 use case 執行。
     *
     * @param gameId 對局 Uuid。
     * @param playerId 發起操作的玩家 Uuid。
     * @param command 欲執行的操作。
     * @return 對應 use case 的執行結果。
     */
    suspend operator fun invoke(
        gameId: Uuid,
        playerId: Uuid,
        command: GameCommand,
    ): Outcome<Unit, GameError> = when (command) {
        is GameCommand.Extension -> extensionCommandRegistry.execute(gameId, playerId, command.value)
        GameCommand.Draw -> drawTileUseCase(gameId, playerId)
        is GameCommand.Discard -> discardTileUseCase(gameId, playerId, command.tileId)
        GameCommand.Tsumo -> declareTsumoUseCase(gameId, playerId)
        is GameCommand.Kan -> declareKanUseCase(gameId, playerId, command.type, command.tileId)
        is GameCommand.RespondToDiscard -> respondToDiscardUseCase(gameId, playerId, command.action)
        is GameCommand.RespondToKan -> respondToKanUseCase(gameId, playerId, command.action)
        is GameCommand.DeclareExhaustiveDraw ->
            declareAbortiveDrawUseCase(gameId, playerId, command.reason)
    }
}
