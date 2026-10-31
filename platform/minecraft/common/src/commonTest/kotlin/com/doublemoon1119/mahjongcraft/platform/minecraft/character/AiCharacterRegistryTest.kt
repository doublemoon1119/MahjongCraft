package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證角色登記不執行原生能力，且描述不會被呼叫端的集合改寫。 */
class AiCharacterRegistryTest {
    /** 登記鍵、凍結與重複檢查使用相同的角色 ID。 */
    @Test
    fun `registration is unique frozen and exposes detached keys`() {
        val registry = AiCharacterRegistryImpl()
        val before = registry.registrationKeys
        registry.register(BuiltInAiCharacters.TILE_MASCOT)
        assertTrue(before.isEmpty())
        assertEquals(setOf(BuiltInAiCharacters.TILE_MASCOT_ID), registry.registrationKeys)
        assertFailsWith<IllegalArgumentException> { registry.register(BuiltInAiCharacters.TILE_MASCOT) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.register(BuiltInAiCharacters.TILE_MASCOT) }
        assertNull(registry.find("example:missing"))
    }

    /** 不需要世界或預覽工廠也能登記只有 FACE 的角色。 */
    @Test
    fun `face only characters and unknown capabilities need no factories`() {
        val registry = AiCharacterRegistryImpl()
        val definition = AiCharacterDefinition("example:face", "example.character.face", AiCharacterFace.Tile("east"), null, null)
        registry.register(definition)
        registry.register(BuiltInAiCharacters.TILE_MASCOT)
        assertEquals(definition, registry.find(definition.id))
        assertEquals(listOf(definition.id, BuiltInAiCharacters.TILE_MASCOT_ID), registry.definitions().map { it.id })
    }

    /** 外觀參數在輸入與讀取時都不共享可變 map。 */
    @Test
    fun `preview parameters are detached and bounded`() {
        val parameters = mutableMapOf("example:coat" to "blue")
        val preview = AiCharacterPreview("example:preview", parameters)
        parameters["example:coat"] = "red"
        assertEquals(mapOf("example:coat" to "blue"), preview.parameters)
        assertFailsWith<IllegalArgumentException> { AiCharacterPreview("invalid", emptyMap()) }
        assertFailsWith<IllegalArgumentException> { AiCharacterPreview("example:preview", mapOf("bad key" to "blue")) }
        assertFailsWith<IllegalArgumentException> { AiCharacterPreview("example:preview", mapOf("example:coat" to "x".repeat(257))) }
    }

    /** 非法資源、座標及名稱在描述建立時拒絕。 */
    @Test
    fun `invalid definitions and face regions are rejected`() {
        assertFailsWith<IllegalArgumentException> { AiCharacterDefinition("invalid", "name", null, null, null) }
        assertFailsWith<IllegalArgumentException> { AiCharacterDefinition("example:face", "", null, null, null) }
        assertFailsWith<IllegalArgumentException> { AiCharacterFace.TextureRegion("example:face", Float.NaN, 0f, 1f, 1f) }
        assertFailsWith<IllegalArgumentException> { AiCharacterFace.TextureRegion("example:face", 0.5f, 0f, 0.2f, 1f) }
    }
}
