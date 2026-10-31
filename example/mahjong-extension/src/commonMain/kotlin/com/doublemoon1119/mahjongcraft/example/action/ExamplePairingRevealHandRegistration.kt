package com.doublemoon1119.mahjongcraft.example.action

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingComponents
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.ExtensionGameActionCommandFactoryRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.api.registration.ExtensionGameCommandExecutorRegistrar
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandlerDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isOwnTurnWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 登記簡易配對麻將攤牌動作的命令工廠。
 *
 * @param registry 要接收攤牌動作工廠的 registry。
 */
fun registerExamplePairingRevealHandFactory(registry: ExtensionGameActionCommandFactoryRegistrar) {
    registry.register(
        ExtensionGameActionCommandFactoryDefinition(ExamplePairingRevealHandAction::class) { _, selectedTileIds ->
            ExamplePairingRevealHandCommand.takeIf { selectedTileIds.isEmpty() }
        },
    )
}

/**
 * 登記簡易配對麻將攤牌命令的權威 handler。
 *
 * @param registry 要接收攤牌命令 handler 的 registry。
 */
fun registerExamplePairingRevealHandHandler(registry: ExtensionGameCommandExecutorRegistrar) {
    registry.register(
        ExtensionGameCommandHandlerDefinition(ExamplePairingRevealHandCommand::class) { context ->
            ExamplePairingRevealHandHandler(context)
        },
    )
}

/**
 * 在權威交易中驗證並提交簡易配對麻將的攤牌命令。
 *
 * @property context 執行擴充命令所需的權威流程服務。
 */
private class ExamplePairingRevealHandHandler(
    private val context: ExtensionGameCommandContext,
) : ExtensionGameCommandHandler<ExamplePairingRevealHandCommand> {
    /**
     * 保存一次權威寫入的結果與寫入後桌況，供後續通知使用。
     *
     * @property outcome 權威驗證與寫入結果。
     * @property tableState 寫入成功後的桌況；拒絕命令時為當前桌況，找不到對局時為 null。
     */
    private data class RevealHandUpdate(
        val outcome: Outcome<Unit, GameError>,
        val tableState: TableState?,
    )

    override suspend fun execute(
        gameId: Uuid,
        playerId: Uuid,
        command: ExamplePairingRevealHandCommand,
    ): Outcome<Unit, GameError> {
        val result = context.gameRepository.updateGame(
            gameId,
            history = { before, after, update ->
                if (update.outcome is Outcome.Success && before != null && after != null) {
                    listOf(
                        acceptedActionHistoryDraft(
                            playerId,
                            GameAction.Extension(ExamplePairingRevealHandAction),
                            before.tableState,
                            after.tableState,
                        ),
                    )
                } else {
                    emptyList()
                }
            },
        ) { game ->
            if (game == null) {
                return@updateGame null to RevealHandUpdate(
                    outcome = Outcome.Error(GameError.GameNotFound(gameId)),
                    tableState = null,
                )
            }
            val state = game.tableState
            if (state.config !is ExamplePairingRuleConfig) {
                return@updateGame game to RevealHandUpdate(
                    outcome = Outcome.Error(GameError.UnsupportedAction(gameId, playerId)),
                    tableState = state,
                )
            }
            val player = state.players.firstOrNull { it.id == playerId }
                ?: return@updateGame game to RevealHandUpdate(
                    outcome = Outcome.Error(GameError.PlayerNotInGame(playerId, gameId)),
                    tableState = state,
                )
            if (
                game.isMatchOver ||
                game.pendingTransition != null ||
                game.pendingRuleDecision != null ||
                !state.isOwnTurnWindowOpenFor(playerId) ||
                state.currentPlayer.hand.lastDrawn == null
            ) {
                return@updateGame game to RevealHandUpdate(
                    outcome = Outcome.Error(GameError.UnsupportedAction(gameId, playerId)),
                    tableState = state,
                )
            }
            if (player.components[ExamplePairingComponents.HAND_REVEALED] != null) {
                return@updateGame game to RevealHandUpdate(
                    outcome = Outcome.Error(GameError.UnsupportedAction(gameId, playerId)),
                    tableState = state,
                )
            }
            val updatedPlayer = player.copy(
                actionHistory = player.actionHistory + GameAction.Extension(ExamplePairingRevealHandAction),
                components = player.components.with(ExamplePairingComponents.HAND_REVEALED, Unit),
            )
            val updatedState = state.copy(
                players = state.players.map { if (it.id == playerId) updatedPlayer else it },
            )
            game.copy(tableState = updatedState) to RevealHandUpdate(
                outcome = Outcome.Success(Unit),
                tableState = updatedState,
            )
        }
        if (result.outcome is Outcome.Error) return result.outcome
        val tableState = checkNotNull(result.tableState) {
            "Successful hand reveal must include the updated table state"
        }
        val action = GameAction.Extension(ExamplePairingRevealHandAction)
        context.snapshotSynchronizer.syncAll(gameId)
        context.eventPublisher.publishToAllObservers(
            gameId,
            tableState.players.map { it.id },
            playerId,
            action,
        )
        context.presentationPublisher.publishGameActionDeclared(gameId, playerId, action)
        context.presentationPublisher.publishRuleStateUpdated(gameId)
        return Outcome.Success(Unit)
    }
}
