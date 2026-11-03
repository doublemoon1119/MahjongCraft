package com.doublemoon1119.mahjongcraft.platform.fabric.server.network

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.NetworkJson
import com.doublemoon1119.mahjongcraft.platform.fabric.network.MahjongChannels
import com.doublemoon1119.mahjongcraft.platform.fabric.server.character.FabricAiCharacterBindings
import com.doublemoon1119.mahjongcraft.platform.fabric.server.persistence.FabricAuthoritativeStatePersistence
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSyncSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.SeatedParticipantSources
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.excludedAppearancesFor
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.toPayload
import net.fabricmc.fabric.api.networking.v1.PlayerLookup
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents
import net.minecraft.server.network.ServerPlayerEntity
import kotlin.uuid.Uuid
import kotlin.uuid.toJavaUuid
import kotlin.uuid.toKotlinUuid

/**
 * 在已授權桌況的同一主執行緒交付點同步公開外觀，不建立任意角色查詢通道。
 *
 * @property persistence 同一權威提交的角色描述與場次。
 * @property json 線路序列化設定，不影響保存格式。
 * @property characters 等待房間內建角色的登記描述。
 * @property bindings 對局中已確認的角色本人。
 */
class AiCharacterSnapshotSender internal constructor(
    private val persistence: FabricAuthoritativeStatePersistence,
    private val json: NetworkJson,
    private val characters: AiCharacterRegistry,
    private val bindings: FabricAiCharacterBindings,
) : AiCharacterSnapshotDelivery {
    /** 各連線與桌子最近實際送出的公開描述，避免每次摸打重送。 */
    private val sent = linkedMapOf<Pair<Any, Uuid>, Sent>()

    /** 補充追蹤狀態可在相同權威版本下改變，交付版本因此獨立單調增加。 */
    private var deliveryRevision = 0L

    /** 登記斷線時的連線快取清理。 */
    fun registerEvents() {
        ServerPlayConnectionEvents.DISCONNECT.register { handler, _ -> sent.keys.removeAll { it.first === handler } }
    }

    /** 伺服器停止時清除前一世界快取。 */
    fun clear() {
        sent.clear()
        deliveryRevision = 0L
    }

    /**
     * 與已授權房間／對局快照一起交付，不可用於未驗證的客戶端要求。
     *
     * @param player 同一交付點已驗證的收件者。
     * @param tableId 同一快照已授權的桌子。
     * @param expectedMatchId 同一內容準備時的場次。
     * @param playerIds 同一內容的完整名冊。
     */
    override fun sendAuthorized(player: ServerPlayerEntity, tableId: Uuid, expectedMatchId: Uuid?, playerIds: Set<Uuid>) {
        check(player.server.isOnThread) { "Character delivery requires the server thread" }
        val state = persistence.characterSnapshot() ?: return
        val metadata = state.characters
        val game = state.authoritative.games[tableId]
        val room = state.authoritative.rooms[tableId]
        if (game == null && room == null) return
        if (game?.matchId != expectedMatchId) return
        val roster = game?.tableState?.players?.map { it.id } ?: room?.playerIds.orEmpty()
        if (roster.toSet() != playerIds) return
        val capturedPlayers = if (metadata == null) {
            emptyMap()
        } else if (game != null) {
            metadata.matchPlayers(game.matchId)
        } else {
            metadata.selectedPlayers(tableId).mapValues { (playerId, id) -> metadata.participant(playerId) ?: AiCharacterSnapshot(id, characters.find(id)) }
        }
        val players = capturedPlayers.filterKeys { it in playerIds }
        val excluded = state.excludedAppearancesFor(tableId, player.uuid.toKotlinUuid())
        val identities = room?.admission?.memberIdentities ?: game?.roomAdmission?.memberIdentities.orEmpty()
        val references = players.keys.mapNotNull { member ->
            val actor = bindings.find(tableId, member)?.entity ?: identities[member]
                ?.takeIf { it.sourceId == SeatedParticipantSources.WORLD_ENTITY }
                ?.let { player.serverWorld.getEntity(it.subjectId.toJavaUuid()) }
            actor?.takeIf { !it.isRemoved && it.world === player.world && player in PlayerLookup.tracking(it) }
                ?.let { member to it.uuid.toKotlinUuid() }
        }.toMap()
        val content = Sent(state.sessionId, game?.matchId, metadata != null, players, excluded, references)
        val key = player.networkHandler to tableId
        if (sent[key] == content) return
        deliveryRevision = maxOf(deliveryRevision + 1L, state.revision)
        val snapshot = AiCharacterSyncSnapshot(
            sessionId = state.sessionId,
            revision = deliveryRevision,
            tableId = tableId,
            isAvailable = metadata != null,
            catalogue = emptyList(),
            players = players,
            matchId = game?.matchId,
            excludedPlayers = excluded,
            worldEntityIds = references,
        )
        MahjongChannels.aiCharacterSnapshot.sendTo(player, json, snapshot.toPayload())
        if (sent.size >= MAX_SENT_ENTRIES && key !in sent) sent.remove(sent.keys.first())
        sent[key] = content
    }

    /** 交付快取的容量限制；淘汰只會使之後重新同步，不影響授權。 */
    private companion object {
        /** 不隨累積拜訪的桌子數量無限成長。 */
        const val MAX_SENT_ENTRIES: Int = 4096
    }

    /**
     * 用內容決定是否需要重送，不以每次權威提交遞增的 revision 當變更判斷。
     *
     * @property session 伺服器載入身分。
     * @property match 這份外觀所屬場次。
     * @property available 外觀資料是否可用。
     * @property players 本次已送出的完整描述。
     * @property excluded 房主可讀的排除名單顯示資料。
     * @property references 只含目前追蹤的即時實體參照。
     */
    private data class Sent(
        val session: Uuid,
        val match: Uuid?,
        val available: Boolean,
        val players: Map<Uuid, AiCharacterSnapshot>,
        val excluded: Map<Uuid, AiCharacterSnapshot>,
        val references: Map<Uuid, Uuid>,
    )
}
