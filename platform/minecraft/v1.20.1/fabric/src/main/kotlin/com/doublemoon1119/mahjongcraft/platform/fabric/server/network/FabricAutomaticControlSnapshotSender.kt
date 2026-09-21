package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.server.game.repository.GameRepository
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/** 使用 Fabric S2C 頻道送出只屬於連線玩家本人的本局自動操作權威快照。 */
@Single(binds = [AutomaticControlSnapshotSender::class])
class FabricAutomaticControlSnapshotSender(
    private val serverHolder: FabricServerHolder,
    private val gameRepository: GameRepository,
    private val moduleRegistry: MahjongModuleRegistry,
    @Provided private val json: Json,
) : AutomaticControlSnapshotSender {
    override suspend fun send(gameId: Uuid, playerId: Uuid) {
        val player = serverHolder.findPlayer(playerId) ?: return
        val game = gameRepository.getGame(gameId)
        if (game == null || game.isMatchOver || game.tableState.players.none { it.id == playerId }) {
            MahjongChannels.automaticControlSnapshot.sendTo(player, json, null)
            return
        }
        val supportedControlIds = moduleRegistry.getModule(game.tableState.config).getSupportedAutomaticControlIds()
        val snapshot = PlayerAutomaticControlSnapshot(
            gameId = game.id,
            revision = game.automaticControlRevision,
            supportedControlIds = supportedControlIds,
            enabledControlIds = game.enabledAutomaticControlIdsByPlayerId[playerId].orEmpty(),
        )
        MahjongChannels.automaticControlSnapshot.sendTo(player, json, snapshot.toDto())
    }

    override fun clear(playerId: Uuid) {
        val player = serverHolder.findPlayer(playerId) ?: return
        MahjongChannels.automaticControlSnapshot.sendTo(player, json, null)
    }
}
