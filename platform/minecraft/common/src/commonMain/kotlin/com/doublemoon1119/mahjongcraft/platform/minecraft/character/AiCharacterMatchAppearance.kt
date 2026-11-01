package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.STANDARD_TILE_ASSET_KEYS
import kotlin.random.Random
import kotlin.uuid.Uuid

/** 為新場次選定內建機器人的造型；不從實際牌山或玩家手牌取樣。 */
object AiCharacterMatchAppearance {
    /**
     * 內建預設角色從標準牌面選一個造型，其他已指定外觀的角色保持原描述。
     *
     * @param definition 登記的角色描述。
     * @param matchId 本場 UUID，提供與遊戲抽牌無關的造型種子。
     * @param playerId AI UUID，讓不同角色各自選擇造型。
     * @return 要寫入本場固定快照的描述；缺少描述時仍為 null。
     */
    fun capture(
        definition: AiCharacterDefinition?,
        matchId: Uuid,
        playerId: Uuid,
    ): AiCharacterDefinition? {
        if (definition == null || definition != BuiltInAiCharacters.TILE_MASCOT) return definition
        val random = Random("$matchId/$playerId".hashCode())
        val face = STANDARD_TILE_ASSET_KEYS[random.nextInt(STANDARD_TILE_ASSET_KEYS.size)]
        return AiCharacterDefinition(
            id = definition.id,
            nameKey = definition.nameKey,
            face = AiCharacterFace.Tile(face),
            worldCapabilityId = definition.worldCapabilityId,
            preview = definition.preview,
        )
    }
}
