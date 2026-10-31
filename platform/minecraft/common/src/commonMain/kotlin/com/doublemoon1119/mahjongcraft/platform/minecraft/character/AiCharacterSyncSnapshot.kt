package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits
import kotlin.uuid.Uuid

/**
 * 一張桌子可同步的公開角色資料，不含世界實體或遊戲私有資訊。
 *
 * @property sessionId 伺服器這次載入的身分。
 * @property revision 這張快照的單調版本。
 * @property tableId 所屬桌子。
 * @property isAvailable 是否允許使用已保存的角色資料。
 * @param catalogue 可選角色目錄。
 * @param players 該桌 AI 的角色描述；空集合可清除已離開的成員。
 */
class AiCharacterSyncSnapshot(
    val sessionId: Uuid,
    val revision: Long,
    val tableId: Uuid,
    val isAvailable: Boolean,
    catalogue: List<AiCharacterDefinition>,
    players: Map<Uuid, AiCharacterSnapshot>,
) {
    /** 不與呼叫端共用的目錄。 */
    private val capturedCatalogue = catalogue.toList()

    /** 不與呼叫端共用的公開成員。 */
    private val capturedPlayers = players.toMap()

    /** 可選目錄的獨立快照。 */
    val catalogue: List<AiCharacterDefinition> get() = capturedCatalogue.toList()

    /** 公開角色的獨立快照。 */
    val players: Map<Uuid, AiCharacterSnapshot> get() = capturedPlayers.toMap()

    init {
        require(revision >= 0L) { "Character sync revision must not be negative" }
        require(capturedCatalogue.size <= AiCharacterLimits.MAX_CATALOGUE_SIZE) { "Character sync catalogue is too large" }
        require(capturedCatalogue.distinctBy { it.id }.size == capturedCatalogue.size) { "Duplicate character sync catalogue ID" }
        require(capturedPlayers.size <= AiCharacterLimits.MAX_SYNC_PLAYERS) { "Too many character sync players" }
        require(isAvailable || capturedPlayers.isEmpty()) { "Unavailable character data cannot expose saved selections" }
    }
}
