package com.doublemoon1119.mahjongcraft.example.action

import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingPlayerState
import com.doublemoon1119.mahjongcraft.example.pairing.ExamplePairingRuleConfig
import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.history.acceptedActionHistoryDraft
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandContext
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandler
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandlerDefinition
import com.doublemoon1119.mahjongcraft.flow.server.game.service.isOwnTurnWindowOpenFor
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import kotlin.uuid.Uuid

/**
 * 登記簡易配對麻將宣告動作的命令工廠。
 *
 * @param registry 要接收宣告動作工廠的 registry。
 */
fun registerExamplePairingDeclarationFactory(registry: ExtensionGameActionCommandFactoryRegistry) {
    registry.register(
        ExtensionGameActionCommandFactoryDefinition(ExamplePairingDeclarationAction::class) { _, selectedTileIds ->
            ExamplePairingDeclarationCommand.takeIf { selectedTileIds.isEmpty() }
        },
    )
}

/**
 * 登記簡易配對麻將宣告命令的權威 handler。
 *
 * @param registry 要接收宣告命令 handler 的 registry。
 */
fun registerExamplePairingDeclarationHandler(registry: ExtensionGameCommandExecutorRegistry) {
    registry.register(
        ExtensionGameCommandHandlerDefinition(ExamplePairingDeclarationCommand::class) { context ->
            ExamplePairingDeclarationHandler(context)
        },
    )
}

/**
 * 在權威交易中驗證並提交簡易配對麻將的宣告命令。
 *
 * @property context 執行擴充命令所需的權威流程服務。
 */
private class ExamplePairingDeclarationHandler(
    private val context: ExtensionGameCommandContext,
) : ExtensionGameCommandHandler<ExamplePairingDeclarationCommand> {
    /**
     * 保存一次權威寫入的結果與寫入後桌況，供後續通知使用。
     *
     * @property outcome 權威驗證與寫入結果。
     * @property tableState 寫入成功後的桌況；拒絕命令時為當前桌況，找不到對局時為 null。
     */
    private data class DeclarationUpdate(
        val outcome: Outcome<Unit, GameError>,
        val tableState: TableState?,
    )

    override suspend fun execute(
        gameId: Uuid,
        playerId: Uuid,
        command: ExamplePairingDeclarationCommand,
    ): Outcome<Unit, GameError> {
        val result = context.gameRepository.updateGame(
            gameId,
            history = { before, after, update ->
                if (update.outcome is Outcome.Success && before != null && after != null) {
                    listOf(
                        acceptedActionHistoryDraft(
                            playerId,
                            GameAction.Extension(ExamplePairingDeclarationAction),
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
                return@updateGame null to DeclarationUpdate(
                    outcome = Outcome.Error(GameError.GameNotFound(gameId)),
                    tableState = null,
                )
            }
            val state = game.tableState
            if (state.config !is ExamplePairingRuleConfig) {
                return@updateGame game to DeclarationUpdate(
                    outcome = Outcome.Error(GameError.UnsupportedAction(gameId, playerId)),
                    tableState = state,
                )
            }
            val player = state.players.firstOrNull { it.id == playerId }
                ?: return@updateGame game to DeclarationUpdate(
                    outcome = Outcome.Error(GameError.PlayerNotInGame(playerId, gameId)),
                    tableState = state,
                )
            if (
                game.isMatchOver ||
                game.pendingTransition != null ||
                game.pendingRoundPreparation != null ||
                !state.isOwnTurnWindowOpenFor(playerId) ||
                state.currentPlayer.hand.lastDrawn == null
            ) {
                return@updateGame game to DeclarationUpdate(
                    outcome = Outcome.Error(GameError.UnsupportedAction(gameId, playerId)),
                    tableState = state,
                )
            }
            val ruleState = player.playerRuleState as? ExamplePairingPlayerState
                ?: return@updateGame game to DeclarationUpdate(
                    outcome = Outcome.Error(GameError.UnsupportedAction(gameId, playerId)),
                    tableState = state,
                )
            if (ruleState.declared) {
                return@updateGame game to DeclarationUpdate(
                    outcome = Outcome.Error(GameError.UnsupportedAction(gameId, playerId)),
                    tableState = state,
                )
            }
            val updatedPlayer = player.copy(
                actionHistory = player.actionHistory + GameAction.Extension(ExamplePairingDeclarationAction),
                playerRuleState = ruleState.copy(declared = true),
            )
            val updatedState = state.copy(
                players = state.players.map { if (it.id == playerId) updatedPlayer else it },
            )
            game.copy(tableState = updatedState) to DeclarationUpdate(
                outcome = Outcome.Success(Unit),
                tableState = updatedState,
            )
        }
        if (result.outcome is Outcome.Error) return result.outcome
        val tableState = checkNotNull(result.tableState) {
            "Successful declaration must include the updated table state"
        }
        val action = GameAction.Extension(ExamplePairingDeclarationAction)
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
