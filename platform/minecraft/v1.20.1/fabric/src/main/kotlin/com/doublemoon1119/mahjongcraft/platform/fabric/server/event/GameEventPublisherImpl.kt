package com.doublemoon1119.mahjongcraft.platform.fabric.server.event

import com.doublemoon1119.mahjongcraft.flow.common.game.repository.GameSnapshotRepository
import com.doublemoon1119.mahjongcraft.flow.common.game.service.GameEventPublisher
import com.doublemoon1119.mahjongcraft.flow.network.dto.command.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.GameUpdatePayloadDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.network.dto.snapshot.toDto
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.history.FabricHistoryOutboxWriter
import com.doublemoon1119.mahjongcraft.platform.fabric.server.network.PlayerIdentitySender
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDelivery
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateTicket
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid

/**
 * [GameEventPublisher] 的 Fabric 實作。呼叫端（各 use case）在呼叫 `publish` 前已經把最新快照寫進
 * [gameSnapshotRepository]，這裡只需要讀出來組 DTO 送出，不用重新呼叫任何 sync use case。
 * 目標玩家不在線或沒有可見快照時不送出；後續同步可取得最新狀態，但不補發這次動作事件。
 * 對局結束時，場次參與者與記錄決策在交付前依已發布的權威狀態記下一次，不受個別封包是否送出影響。
 *
 * @property gameSnapshotRepository 玩家可見的對局快照來源。
 * @property tableStateDelivery 統一檢查桌子來源並序列化桌況封包交付的邊界。
 * @property authoritativeStateStore 讀取仍存在的權威對局與歷史記錄資格。
 * @property serverHolder 目前伺服器與有效收件玩家。
 * @property playerIdentities 已授權快照參與者的普通名稱同步器。
 * @property historyWriter 最近結束場次的保存狀態授權索引。
 * @property json 封包序列化設定。
 * @property networkRegistries 規則擴充資料的線路轉換註冊表。
 */
class GameEventPublisherImpl(
    private val gameSnapshotRepository: GameSnapshotRepository,
    private val tableStateDelivery: FabricTableStateDelivery,
    private val authoritativeStateStore: AuthoritativeStateStore,
    private val serverHolder: FabricServerHolder,
    private val playerIdentities: PlayerIdentitySender,
    private val historyWriter: FabricHistoryOutboxWriter,
    private val json: NetworkJson,
    private val networkRegistries: NetworkDtoRegistries,
) : GameEventPublisher {
    override suspend fun publish(gameId: Uuid, targetPlayerId: Uuid, actorId: Uuid, action: GameAction) {
        rememberEndedMatch(gameId, action)
        val ticket = tableStateDelivery.capture(gameId) ?: return
        publish(ticket, gameId, targetPlayerId, actorId, action)
    }

    /**
     * 對局結束時記下場次參與者與記錄決策，讓客戶端收到結束事件後即可查詢保存狀態。
     *
     * @param gameId 結束的對局。
     * @param action 本次發布的動作；不是對局結束時不做事。
     */
    private fun rememberEndedMatch(gameId: Uuid, action: GameAction) {
        if (action !is GameAction.MatchEnded) return
        val state = authoritativeStateStore.state.value
        val game = state.games[gameId] ?: return
        historyWriter.rememberEndedMatch(
            matchId = game.matchId,
            participantIds = game.tableState.players.mapTo(mutableSetOf()) { it.id },
            decision = state.historyRecordingState.decisionsByMatchId[game.matchId],
        )
    }

    /** 使用呼叫端捕捉的同一張桌子來源票據發布對局事件。 */
    private suspend fun publish(
        ticket: FabricTableStateTicket,
        gameId: Uuid,
        targetPlayerId: Uuid,
        actorId: Uuid,
        action: GameAction,
    ) {
        tableStateDelivery.prepareAndSend(
            ticket = ticket,
            prepare = {
                val player = serverHolder.findPlayer(targetPlayerId) ?: return@prepareAndSend null
                val snapshot = gameSnapshotRepository.getSnapshot(gameId, targetPlayerId) ?: return@prepareAndSend null
                val game = authoritativeStateStore.getGame(gameId) ?: return@prepareAndSend null
                val endedGame = game.takeIf { action is GameAction.MatchEnded }
                val aiPlayerIds = game.aiPlayerIds
                playerIdentities.send(targetPlayerId, snapshot.players.map { it.id }.filterNot { it in aiPlayerIds })
                PreparedGameUpdate(
                    player = player,
                    payload = GameUpdatePayloadDto(
                        gameId = gameId.toString(),
                        actorId = actorId.toString(),
                        action = action.toDto(networkRegistries),
                        snapshot = snapshot.toDto(networkRegistries),
                        aiPlayerIds = aiPlayerIds.map(Uuid::toString),
                        historyMatchId = endedGame?.matchId?.toString(),
                    ),
                )
            },
            send = { prepared -> MahjongChannels.gameUpdate.sendTo(prepared.player, json, prepared.payload) },
        )
    }

    override suspend fun publishToAllObservers(gameId: Uuid, seatedPlayerIds: Collection<Uuid>, actorId: Uuid, action: GameAction) {
        rememberEndedMatch(gameId, action)
        val ticket = tableStateDelivery.capture(gameId) ?: return
        val targetPlayerIds = seatedPlayerIds.toMutableSet()
        targetPlayerIds += gameSnapshotRepository.getAllObservers(gameId)
        targetPlayerIds.forEach { targetPlayerId -> publish(ticket, gameId, targetPlayerId, actorId, action) }
    }

    /**
     * 已在主執行緒準備完成、可在最後檢查後立即送出的對局更新。
     *
     * @property player 已驗證仍在線的收件玩家。
     * @property payload 已序列化所需內容的對局更新 payload。
     */
    private data class PreparedGameUpdate(
        val player: ServerPlayerEntity,
        val payload: GameUpdatePayloadDto,
    )
}
