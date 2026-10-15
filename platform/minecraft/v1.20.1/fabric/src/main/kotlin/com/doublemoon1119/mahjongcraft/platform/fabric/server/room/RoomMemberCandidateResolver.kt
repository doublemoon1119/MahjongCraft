package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import kotlin.uuid.Uuid

/**
 * 依玩家目前所在房間，列出除房主外的所有候選成員，供 `kick`、`ai strategy` 等指令的目標引數共用。
 *
 * AI 玩家的序號依 [Room.playerIds] 的加入
 * 順序重新編號（每次呼叫都重新計算，不做跨呼叫的持久化）。
 *
 * @property membershipRepository 玩家唯一麻將桌歸屬倉庫，用於解析玩家目前所在房間。
 * @property roomRepository 權威房間數據倉庫。
 * @property serverHolder 用於將真人玩家 Uuid 解析成目前的 Minecraft 使用者名稱。
 */
class RoomMemberCandidateResolver(
    private val membershipRepository: PlayerMembershipRepository,
    private val roomRepository: RoomRepository,
    private val serverHolder: FabricServerHolder,
) {
    /**
     * 列出 [playerId] 目前所在房間內，除房主外的所有候選成員。
     *
     * @param playerId 用來解析目前房間的玩家 UUID。
     * @return 房間候選成員；玩家沒有房間或房間不存在時為空清單。
     */
    suspend fun listCandidates(playerId: Uuid): List<RoomMemberCandidate> {
        val tableId = membershipRepository.getVenueId(playerId) ?: return emptyList()
        val room = roomRepository.getRoom(tableId) ?: return emptyList()

        var aiSequence = 0
        return (room.playerIds - room.hostId).mapNotNull { memberId ->
            if (room.isAi(memberId)) {
                aiSequence += 1
                RoomMemberCandidate(
                    playerId = memberId,
                    token = "ai_$aiSequence",
                    aiSequence = aiSequence,
                    strategyKey = room.aiPlayerStrategyKeys[memberId],
                )
            } else {
                val name = serverHolder.findPlayer(memberId)?.gameProfile?.name ?: return@mapNotNull null
                RoomMemberCandidate(memberId, token = name)
            }
        }
    }

    /**
     * 列出 [playerId] 目前所在房間內，除房主外的所有 AI 候選成員。
     *
     * @param playerId 用來解析目前房間的玩家 UUID。
     * @return 房間內可作為 AI 策略變更目標的候選成員。
     */
    suspend fun listAiCandidates(playerId: Uuid): List<RoomMemberCandidate> = listCandidates(playerId)
        .filter { it.aiSequence != null }

    /**
     * 解析 [playerId] 目前所在房間內，與 [token] 完全相符的候選成員 Uuid。
     *
     * @param playerId 用來解析目前房間的玩家 UUID。
     * @param token 指令輸入的完整候選 token。
     * @return 相符的成員 UUID；找不到時為 null。
     */
    suspend fun resolve(playerId: Uuid, token: String): Uuid? = listCandidates(playerId)
        .firstOrNull { it.token == token }
        ?.playerId
}
