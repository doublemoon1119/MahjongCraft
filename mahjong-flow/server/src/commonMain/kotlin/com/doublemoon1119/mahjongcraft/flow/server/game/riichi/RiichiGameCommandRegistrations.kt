package com.doublemoon1119.mahjongcraft.flow.server.game.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiGameCommand
import com.doublemoon1119.mahjongcraft.flow.common.result.Outcome
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameActionCommandFactoryRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandExecutorRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.ExtensionGameCommandHandler
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import kotlin.uuid.Uuid

/** 登記內建日麻立直命令的 handler；每個執行環境以自己的流程服務建立 [DeclareRiichiUseCase]。 */
fun ExtensionGameCommandExecutorRegistry.registerRiichiGameCommandHandler() {
    register(RiichiGameCommand::class) { context ->
        val declareRiichiUseCase = DeclareRiichiUseCase(
            gameRepository = context.gameRepository,
            moduleRegistry = context.moduleRegistry,
            snapshotSynchronizer = context.snapshotSynchronizer,
            handSortPreferenceStore = context.handSortPreferenceStore,
            postActionExhaustiveDrawResolverRegistry = context.postActionExhaustiveDrawResolverRegistry,
            eventPublisher = context.eventPublisher,
            presentationPublisher = context.presentationPublisher,
        )
        object : ExtensionGameCommandHandler<RiichiGameCommand> {
            override suspend fun execute(
                gameId: Uuid,
                playerId: Uuid,
                command: RiichiGameCommand,
            ): Outcome<Unit, GameError> = declareRiichiUseCase(gameId, playerId, command.tileId)
        }
    }
}

/** 登記內建日麻立直動作的命令 factory。 */
fun ExtensionGameActionCommandFactoryRegistry.registerRiichiGameActionCommandFactory() {
    register(RiichiGameAction.Riichi::class) { _, selectedTileIds ->
        selectedTileIds.singleOrNull()?.let(::RiichiGameCommand)
    }
}
