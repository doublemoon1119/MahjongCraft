package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY

/** 不依賴外部服務的內建角色描述；原生能力由對應平台實作。 */
object BuiltInAiCharacters {
    /** 麻將牌吉祥物的穩定角色 ID。 */
    const val TILE_MASCOT_ID: String = "mahjongcraft:tile_mascot"

    /** 世界代理及預覽共用的內建能力 ID。 */
    const val TILE_MASCOT_CAPABILITY_ID: String = "mahjongcraft:tile_mascot"

    /** 角色名稱的翻譯鍵。 */
    const val TILE_MASCOT_NAME_KEY: String = "mahjongcraft.character.tile_mascot"

    /** 內建角色的純資料描述；不會在存讀檔時建立實體。 */
    val TILE_MASCOT: AiCharacterDefinition = AiCharacterDefinition(
        id = TILE_MASCOT_ID,
        nameKey = TILE_MASCOT_NAME_KEY,
        face = AiCharacterFace.Tile(UNKNOWN_TILE_ASSET_KEY),
        worldCapabilityId = TILE_MASCOT_CAPABILITY_ID,
        preview = AiCharacterPreview(TILE_MASCOT_CAPABILITY_ID, emptyMap()),
    )
}
