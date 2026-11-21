package com.doublemoon1119.mahjongcraft.platform.minecraft.history

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY

/** 牌譜回放中 AI 角色的畫法：內建機器人外觀或角色自己的預覽模型。 */
object HistoryReplayCharacterAppearance {
    /**
     * 判斷角色是否改用自己的預覽模型畫出；內建機器人能力的角色與沒有預覽能力的角色都畫成內建機器人。
     *
     * @param definition 該場保存的角色描述；沒有時為 null。
     * @return 使用預覽模型時為 true。
     */
    fun usesPreviewModel(definition: AiCharacterDefinition?): Boolean {
        val capabilityId = definition?.preview?.capabilityId ?: return false
        return capabilityId != BuiltInAiCharacters.TILE_MASCOT_CAPABILITY_ID
    }

    /**
     * 畫成內建機器人時臉上的牌面：角色以牌面當頭像時用那張牌，其他情況用未知牌面。
     *
     * @param definition 該場保存的角色描述；沒有時為 null。
     * @return 牌面素材識別鍵。
     */
    fun botFace(definition: AiCharacterDefinition?): String = (definition?.face as? AiCharacterFace.Tile)?.assetKey ?: UNKNOWN_TILE_ASSET_KEY
}
