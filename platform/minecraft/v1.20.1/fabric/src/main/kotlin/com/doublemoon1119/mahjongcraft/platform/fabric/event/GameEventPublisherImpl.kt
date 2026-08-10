package com.doublemoon1119.mahjongcraft.platform.fabric.event

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.dto.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.dto.toDto
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * [GameEventPublisher] 的 Fabric 實作。呼叫端（各 use case）在呼叫 `publish` 前已經把最新快照寫進
 * [gameSnapshotRepository]，這裡只需要讀出來組 DTO 送出，不用重新呼叫任何 sync use case。
 * [targetPlayerId] 若目前不在線（[FabricServerHolder.findPlayer] 回傳 null），直接跳過——快照已經
 * 留在 repository 裡，等玩家下次同步即可，不是遺失事件。
 */
@Single(binds = [GameEventPublisher::class])
class GameEventPublisherImpl(
    private val gameSnapshotRepository: GameSnapshotRepository,
    private val serverHolder: FabricServerHolder,
    private val json: Json,
) : GameEventPublisher {
    override suspend fun publish(gameId: Uuid, targetPlayerId: Uuid, actorId: Uuid, action: GameAction) {
        val player = serverHolder.findPlayer(targetPlayerId) ?: return
        val snapshot = gameSnapshotRepository.getSnapshot(gameId, targetPlayerId) ?: return
        val payload = GameUpdatePayloadDto(
            gameId = gameId.toString(),
            actorId = actorId.toString(),
            action = action.toDto(),
            snapshot = snapshot.toDto(),
        )
        MahjongChannels.gameUpdate.sendTo(player, json, payload)
    }
}
