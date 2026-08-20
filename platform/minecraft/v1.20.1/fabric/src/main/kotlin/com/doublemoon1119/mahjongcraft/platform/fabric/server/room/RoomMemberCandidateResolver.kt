package com.doublemoon1119.mahjongcraft.platform.fabric.server.room

import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.server.membership.repository.PlayerMembershipRepository
import com.doublemoon1119.mahjongcraft.flow.server.room.repository.RoomRepository
import com.doublemoon1119.mahjongcraft.platform.fabric.server.FabricServerHolder
import org.koin.core.annotation.Single
import kotlin.uuid.Uuid

/**
 * `/mahjongcraft room kick`／`ai strategy` 等指令共用的房間成員候選目標。
 *
 * @property playerId 實際指向的成員 Uuid。
 * @property token 打進聊天輸入框的實際文字。伺服器端在 1.20.1 無法得知玩家客戶端語言，Brigadier
 *   補全文字本身也只能是純字串，不能是可依語言翻譯的 [net.minecraft.text.Text]，因此固定用語言無關
 *   的技術性代號（真人玩家則直接用其 Minecraft 使用者名稱，本來就無需翻譯）。
 * @property aiSequence 這個候選是 AI 時，牠在目前房間內的顯示序號；真人玩家為 `null`。呼叫端用這個
 *   序號組出 `Text.translatable` tooltip，翻譯交給客戶端依玩家語言處理。
 * @property strategyKey 這個候選是 AI 時，牠登記使用的 AI 策略 key；真人玩家為 `null`。
 */
data class RoomMemberCandidate(
    val playerId: Uuid,
    val token: String,
    val aiSequence: Int? = null,
    val strategyKey: String? = null,
)

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
@Single
class RoomMemberCandidateResolver(
    private val membershipRepository: PlayerMembershipRepository,
    private val roomRepository: RoomRepository,
    private val serverHolder: FabricServerHolder,
) {
    /** 列出 [playerId] 目前所在房間內，除房主外的所有候選成員。 */
    suspend fun listCandidates(playerId: Uuid): List<RoomMemberCandidate> {
        val tableId = membershipRepository.getTableId(playerId) ?: return emptyList()
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

    /** 列出 [playerId] 目前所在房間內，除房主外的所有 AI 候選成員。 */
    suspend fun listAiCandidates(playerId: Uuid): List<RoomMemberCandidate> = listCandidates(playerId)
        .filter { it.aiSequence != null }

    /** 解析 [playerId] 目前所在房間內，與 [token] 完全相符的候選成員 Uuid。 */
    suspend fun resolve(playerId: Uuid, token: String): Uuid? = listCandidates(playerId)
        .firstOrNull { it.token == token }
        ?.playerId
}
