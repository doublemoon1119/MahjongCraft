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

    /** 內建牌體角色的四肢模型，沿用標準牌體圖集。 */
    const val TILE_MASCOT_LIMB_MODEL_ID: String = "mahjongcraft:entity/mahjong_bot_limb"

    /** 角色名稱的翻譯鍵。 */
    const val TILE_MASCOT_NAME_KEY: String = "mahjongcraft.character.tile_mascot"

    /** 內建角色的預設描述；新場次的具體造型由開局快照固定，沒有場次時使用未知牌面。 */
    val TILE_MASCOT: AiCharacterDefinition = AiCharacterDefinition(
        id = TILE_MASCOT_ID,
        nameKey = TILE_MASCOT_NAME_KEY,
        face = AiCharacterFace.Tile(UNKNOWN_TILE_ASSET_KEY),
        worldCapabilityId = TILE_MASCOT_CAPABILITY_ID,
        preview = AiCharacterPreview(TILE_MASCOT_CAPABILITY_ID, emptyMap()),
    )
}
