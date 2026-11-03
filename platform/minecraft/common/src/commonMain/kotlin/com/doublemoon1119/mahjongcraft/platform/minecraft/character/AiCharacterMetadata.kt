package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits
import kotlin.uuid.Uuid

/**
 * 平台保存的角色選擇與開局快照，不含世界物件。
 *
 * @param selections 依桌子、AI UUID 索引的目前角色 ID。
 * @param matches 依場次、AI UUID 索引的固定開局描述。
 * @param participants 關聯成員及房間暫時排除者的描述；後者只供房主辨識。
 */
class AiCharacterMetadata(
    selections: Map<Uuid, Map<Uuid, String>>,
    matches: Map<Uuid, Map<Uuid, AiCharacterSnapshot>>,
    participants: Map<Uuid, AiCharacterSnapshot> = emptyMap(),
) {
    /** 不與呼叫端共用的角色選擇。 */
    private val capturedSelections = selections.mapValues { (_, players) -> players.toMap() }.toMap()

    /** 不與呼叫端共用的開局快照。 */
    private val capturedMatches = matches.mapValues { (_, players) -> players.toMap() }.toMap()

    /** 目前角色選擇的獨立快照。 */
    val selections: Map<Uuid, Map<Uuid, String>> get() = capturedSelections.mapValues { (_, players) -> players.toMap() }

    /** 按場次固定的角色快照。 */
    val matches: Map<Uuid, Map<Uuid, AiCharacterSnapshot>> get() = capturedMatches.mapValues { (_, players) -> players.toMap() }

    /** 關聯成員及暫時排除者描述的獨立快照。 */
    private val capturedParticipants = participants.toMap()

    /** 關聯成員及暫時排除者描述的獨立快照。 */
    val participants: Map<Uuid, AiCharacterSnapshot> get() = capturedParticipants.toMap()

    /**
     * 只複製指定場次的名冊，不為單桌同步重建所有對局的 map。
     *
     * @param matchId 已固定外觀的場次。
     * @return 該場次的獨立快照，沒有時為空。
     */
    fun matchPlayers(matchId: Uuid): Map<Uuid, AiCharacterSnapshot> = capturedMatches[matchId].orEmpty().toMap()

    /**
     * 只複製指定房間的選擇。
     *
     * @param tableId 房間所在桌子。
     * @return 目前選擇的獨立快照。
     */
    fun selectedPlayers(tableId: Uuid): Map<Uuid, String> = capturedSelections[tableId].orEmpty().toMap()

    /**
     * 取得一位外部參與者已擷取的不可變描述。
     *
     * @param playerId 成員編號。
     * @return 目前擷取的外觀，沒有時為 null。
     */
    fun participant(playerId: Uuid): AiCharacterSnapshot? = capturedParticipants[playerId]

    init {
        capturedSelections.values.forEach { players -> players.values.forEach(AiCharacterLimits::requireId) }
    }

    /** 內容相同的 metadata 相等。 */
    override fun equals(other: Any?): Boolean = other is AiCharacterMetadata && capturedSelections == other.capturedSelections && capturedMatches == other.capturedMatches && capturedParticipants == other.capturedParticipants

    /** 與相等規則一致的雜湊值。 */
    override fun hashCode(): Int = 31 * (31 * capturedSelections.hashCode() + capturedMatches.hashCode()) + capturedParticipants.hashCode()
}
