package com.doublemoon1119.mahjongcraft.example.minecraft.character

import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleCharacterMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview

/** 可由各版本轉接層登記的角色描述，不建立實體或查詢世界。 */
object ExampleCharacters {
    /** 人偶、建立物品與角色共用的名稱翻譯鍵。 */
    const val NAME_KEY = ExampleCharacterMessageKeys.ACTOR

    /** 以獨立模型產生備援頭像的借用角色。 */
    val ACTOR: AiCharacterDefinition = AiCharacterDefinition(
        id = ExampleCharacterIds.ACTOR,
        nameKey = NAME_KEY,
        face = null,
        worldCapabilityId = ExampleCharacterIds.ACTOR,
        preview = AiCharacterPreview(ExampleCharacterIds.ACTOR, emptyMap()),
    )

    /** 自有代理的測試描述，不提供房主角色選擇畫面。 */
    val OWNED_ACTOR: AiCharacterDefinition = AiCharacterDefinition(
        id = ExampleCharacterIds.OWNED_ACTOR,
        nameKey = NAME_KEY,
        face = null,
        worldCapabilityId = ExampleCharacterIds.OWNED_ACTOR,
        preview = AiCharacterPreview(ExampleCharacterIds.ACTOR, emptyMap()),
    )

    /** 明確頭像的測試描述，缺少世界或預覽能力不影響其 AI 身分。 */
    val FACE_ONLY: AiCharacterDefinition = AiCharacterDefinition(
        id = ExampleCharacterIds.FACE_ONLY,
        nameKey = NAME_KEY,
        face = AiCharacterFace.TextureRegion(
            resourceId = "minecraft:textures/item/armor_stand.png",
            u0 = 0f,
            v0 = 0f,
            u1 = 1f,
            v1 = 1f,
        ),
        worldCapabilityId = null,
        preview = null,
    )

    /** 同一組描述供註冊及測試核對。 */
    fun definitions(): List<AiCharacterDefinition> = listOf(
        ACTOR,
        OWNED_ACTOR,
        FACE_ONLY,
    )
}
