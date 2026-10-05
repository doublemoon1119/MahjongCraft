package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import kotlinx.serialization.json.Json
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * [GameEventPublisher] 的 Fabric 實作。呼叫端（各 use case）在呼叫 `publish` 前已經把最新快照寫進
 * [gameSnapshotRepository]，這裡只需要讀出來組 DTO 送出，不用重新呼叫任何 sync use case。
 * 目標玩家若目前不在線（[FabricServerHolder.findPlayer] 回傳 null），直接跳過——快照已經
 * 留在 repository 裡，等玩家下次同步即可，不是遺失事件。
 *
 * @property gameSnapshotRepository 玩家可見的對局快照來源。
 * @property authoritativeStateStore 對局結束時的權威場次識別與記錄決策來源。
 * @property serverHolder 目前伺服器與有效收件玩家。
 * @property playerIdentities 已授權快照參與者的普通名稱同步器。
 * @property historyWriter 最近結束場次的保存狀態授權索引。
 * @property json 封包序列化設定。
 * @property networkRegistries 規則擴充資料的線路轉換註冊表。
 */
@Single(binds = [GameEventPublisher::class])
class GameEventPublisherImpl(
    private val gameSnapshotRepository: GameSnapshotRepository,
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val serverHolder: FabricServerHolder,
    private val playerIdentities: PlayerIdentitySender,
    private val historyWriter: FabricHistoryOutboxWriter,
    @Provided private val json: Json,
    @Provided private val networkRegistries: NetworkDtoRegistries,
) : GameEventPublisher {
    override suspend fun publish(gameId: Uuid, targetPlayerId: Uuid, actorId: Uuid, action: GameAction) {
        val player = serverHolder.findPlayer(targetPlayerId) ?: return
        val snapshot = gameSnapshotRepository.getSnapshot(gameId, targetPlayerId) ?: return
        val game = authoritativeStateStore.getGame(gameId)
        val endedGame = game?.takeIf { action is GameAction.MatchEnded }
        val aiPlayerIds = game?.aiPlayerIds.orEmpty()
        endedGame?.let { game ->
            val decision = authoritativeStateStore.snapshot().historyRecordingState.decisionsByMatchId[game.matchId]
            historyWriter.rememberEndedMatch(game.matchId, snapshot.players.mapTo(mutableSetOf()) { it.id }, decision)
        }
        val payload = GameUpdatePayloadDto(
            gameId = gameId.toString(),
            actorId = actorId.toString(),
            action = action.toDto(networkRegistries),
            snapshot = snapshot.toDto(networkRegistries),
            aiPlayerIds = aiPlayerIds.map(Uuid::toString),
            historyMatchId = endedGame?.matchId?.toString(),
        )
        playerIdentities.send(targetPlayerId, snapshot.players.map { it.id }.filterNot { it in aiPlayerIds })
        MahjongChannels.gameUpdate.sendTo(player, json, payload)
    }

    override suspend fun publishToAllObservers(gameId: Uuid, seatedPlayerIds: Collection<Uuid>, actorId: Uuid, action: GameAction) {
        val targetPlayerIds = seatedPlayerIds.toMutableSet()
        targetPlayerIds += gameSnapshotRepository.getAllObservers(gameId)
        targetPlayerIds.forEach { targetPlayerId -> publish(gameId, targetPlayerId, actorId, action) }
    }
}
