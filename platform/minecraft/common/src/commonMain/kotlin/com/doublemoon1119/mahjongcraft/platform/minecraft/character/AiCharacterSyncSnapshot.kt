package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits
import kotlin.uuid.Uuid

/**
 * 一張桌子可同步的公開角色資料，只含已授權外觀與目前追蹤的實體參照，不含遊戲私有資訊。
 *
 * @property sessionId 伺服器這次載入的身分。
 * @property revision 這張快照的單調版本。
 * @property tableId 所屬桌子。
 * @property isAvailable 是否允許使用已保存的角色資料。
 * @param catalogue 可選角色目錄。
 * @param players 該桌 AI 的角色描述；空集合可清除已離開的成員。
 * @property matchId 本份資料對應的場次；等待房間為 null。
 * @param excludedPlayers 只有房主可讀的已排除角色顯示資料。
 * @param worldEntityIds 目前收件者正追蹤的成員實體 UUID；只供即時預覽，不保存或提供給歷史。
 */
class AiCharacterSyncSnapshot(
    val sessionId: Uuid,
    val revision: Long,
    val tableId: Uuid,
    val isAvailable: Boolean,
    catalogue: List<AiCharacterDefinition>,
    players: Map<Uuid, AiCharacterSnapshot>,
    val matchId: Uuid? = null,
    excludedPlayers: Map<Uuid, AiCharacterSnapshot> = emptyMap(),
    worldEntityIds: Map<Uuid, Uuid> = emptyMap(),
) {
    /** 此次授權追蹤的實體參照，不與呼叫端共用。 */
    private val capturedWorldEntityIds = worldEntityIds.toMap()

    /** 即時預覽可尋找的已載入實體，不授予操作權限。 */
    val worldEntityIds: Map<Uuid, Uuid> get() = capturedWorldEntityIds.toMap()

    /** 房主可讀的獨立描述，不進入目前成員反向索引。 */
    private val capturedExcludedPlayers = excludedPlayers.toMap()

    /** 每次讀取取得獨立集合，不能藉由轉型修改已接受內容。 */
    val excludedPlayers: Map<Uuid, AiCharacterSnapshot> get() = capturedExcludedPlayers.toMap()

    /** 不與呼叫端共用的目錄。 */
    private val capturedCatalogue = catalogue.toList()

    /** 不與呼叫端共用的公開成員。 */
    private val capturedPlayers = players.toMap()

    /** 可選目錄的獨立快照。 */
    val catalogue: List<AiCharacterDefinition> get() = capturedCatalogue.toList()

    /** 公開角色的獨立快照。 */
    val players: Map<Uuid, AiCharacterSnapshot> get() = capturedPlayers.toMap()

    init {
        require(worldEntityIds.keys.all { it in capturedPlayers }) { "World entity references must belong to visible character players" }
        require(worldEntityIds.values.toSet().size == worldEntityIds.size) { "A world entity cannot represent multiple players" }
        require(revision >= 0L) { "Character sync revision must not be negative" }
        require(capturedCatalogue.size <= AiCharacterLimits.MAX_CATALOGUE_SIZE) { "Character sync catalogue is too large" }
        require(capturedCatalogue.distinctBy { it.id }.size == capturedCatalogue.size) { "Duplicate character sync catalogue ID" }
        require(capturedPlayers.size <= AiCharacterLimits.MAX_SYNC_PLAYERS) { "Too many character sync players" }
        require(excludedPlayers.size <= RoomAdmissionState.MAX_EXCLUDED_PARTICIPANTS) { "Too many excluded character players" }
        require(isAvailable || (capturedPlayers.isEmpty() && excludedPlayers.isEmpty())) { "Unavailable character data cannot expose saved selections" }
    }
}
