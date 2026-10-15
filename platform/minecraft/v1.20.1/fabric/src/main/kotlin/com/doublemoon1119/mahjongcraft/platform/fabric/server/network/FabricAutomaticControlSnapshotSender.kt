package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.common.game.model.PlayerAutomaticControlSnapshot
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.AutomaticControlSnapshotDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.message.toDto
import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.state.AuthoritativeStateStore
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import com.doublemoon1119.mahjongcraft.platform.fabric.server.table.FabricTableStateDelivery
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid

/**
 * 使用 Fabric S2C 頻道送出只屬於連線玩家本人的本局自動操作權威快照。
 *
 * @property serverHolder 查找目前連線玩家。
 * @property stateStore 提供當前已發布對局與自動操作狀態。
 * @property memberships 確認收件玩家仍屬於此桌，不清除他正在另一桌使用的狀態。
 * @property tableStateDelivery 送出前確認桌子來源仍有效的共同交付序列。
 * @property moduleRegistry 解析規則支援的自動操作項目。
 * @property json 序列化網路訊息的設定。
 */
class FabricAutomaticControlSnapshotSender(
    private val serverHolder: FabricServerHolder,
    private val stateStore: AuthoritativeStateStore,
    private val memberships: PlayerMembershipRepository,
    private val tableStateDelivery: FabricTableStateDelivery,
    private val moduleRegistry: MahjongModuleRegistry,
    private val json: NetworkJson,
) : AutomaticControlSnapshotSender {
    override suspend fun send(gameId: Uuid, playerId: Uuid) {
        val ticket = tableStateDelivery.capture(gameId) ?: return
        tableStateDelivery.prepareAndSend(
            ticket = ticket,
            prepare = {
                if (memberships.getVenueId(playerId) != gameId) return@prepareAndSend null
                val player = serverHolder.findPlayer(playerId) ?: return@prepareAndSend null
                val game = stateStore.state.value.games[gameId] ?: return@prepareAndSend null
                if (game.tableState.players.none { it.id == playerId }) return@prepareAndSend null
                val snapshot = if (game.isMatchOver) {
                    null
                } else {
                    PlayerAutomaticControlSnapshot(
                        gameId = game.id,
                        revision = game.automaticControlRevision,
                        supportedControlIds = moduleRegistry.getModule(game.tableState.config).getSupportedAutomaticControlIds(),
                        enabledControlIds = game.enabledAutomaticControlIdsByPlayerId[playerId].orEmpty(),
                    ).toDto()
                }
                PreparedControl(player, snapshot)
            },
            send = { prepared -> MahjongChannels.automaticControlSnapshot.sendTo(prepared.player, json, prepared.snapshot) },
        )
    }

    override fun clear(playerId: Uuid) {
        val server = serverHolder.current() ?: return
        val sessionId = serverHolder.sessionId()
        server.execute {
            if (serverHolder.current() !== server || serverHolder.sessionId() != sessionId) return@execute
            val player = serverHolder.findPlayer(playerId) ?: return@execute
            MahjongChannels.automaticControlSnapshot.sendTo(player, json, null)
        }
    }

    /**
     * 已完成本人與來源檢查的自動操作交付資料。
     *
     * @property player 仍在本次啟動連線中的收件玩家。
     * @property snapshot 本人目前的操作集合；終局時為 null。
     */
    private class PreparedControl(val player: ServerPlayerEntity, val snapshot: AutomaticControlSnapshotDto?)
}
