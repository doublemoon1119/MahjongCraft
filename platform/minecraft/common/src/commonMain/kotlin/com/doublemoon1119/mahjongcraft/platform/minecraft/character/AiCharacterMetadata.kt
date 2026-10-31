package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterLimits
import kotlin.uuid.Uuid

/**
 * 平台保存的角色選擇與開局快照，不含世界物件。
 *
 * @param selections 依桌子、AI UUID 索引的目前角色 ID。
 * @param matches 依場次、AI UUID 索引的固定開局描述。
 */
class AiCharacterMetadata(
    selections: Map<Uuid, Map<Uuid, String>>,
    matches: Map<Uuid, Map<Uuid, AiCharacterSnapshot>>,
) {
    /** 不與呼叫端共用的角色選擇。 */
    private val capturedSelections = selections.mapValues { (_, players) -> players.toMap() }.toMap()

    /** 不與呼叫端共用的開局快照。 */
    private val capturedMatches = matches.mapValues { (_, players) -> players.toMap() }.toMap()

    /** 目前角色選擇的獨立快照。 */
    val selections: Map<Uuid, Map<Uuid, String>> get() = capturedSelections.mapValues { (_, players) -> players.toMap() }

    /** 按場次固定的角色快照。 */
    val matches: Map<Uuid, Map<Uuid, AiCharacterSnapshot>> get() = capturedMatches.mapValues { (_, players) -> players.toMap() }

    init {
        capturedSelections.values.forEach { players -> players.values.forEach(AiCharacterLimits::requireId) }
    }

    /** 內容相同的 metadata 相等。 */
    override fun equals(other: Any?): Boolean = other is AiCharacterMetadata && capturedSelections == other.capturedSelections && capturedMatches == other.capturedMatches

    /** 與相等規則一致的雜湊值。 */
    override fun hashCode(): Int = 31 * capturedSelections.hashCode() + capturedMatches.hashCode()
}
