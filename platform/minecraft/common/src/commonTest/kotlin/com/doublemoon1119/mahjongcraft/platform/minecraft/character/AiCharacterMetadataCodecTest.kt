package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterPreview
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 保存格式不查詢目前登記表，並拒絕遺漏欄位或重複鍵。 */
class AiCharacterMetadataCodecTest {
    /** 完整材質區域、預覽參數及未知角色 ID 都能往返。 */
    @Test
    fun `all appearance fields round trip without a registry`() {
        val player = Uuid.random()
        val definition = AiCharacterDefinition("example:actor", "example.actor", AiCharacterFace.TextureRegion("example:textures/face.png", 0f, 0.1f, 0.5f, 1f), "example:world", AiCharacterPreview("example:preview", mapOf("example:coat" to "blue")))
        val expected = AiCharacterMetadata(mapOf(Uuid.random() to mapOf(player to "missing:actor")), mapOf(Uuid.random() to mapOf(player to AiCharacterSnapshot(definition.id, definition))))
        val codec = AiCharacterMetadataCodec()
        assertEquals(expected, codec.decode(codec.encode(expected)))
        assertEquals(codec.encode(expected), codec.encode(codec.decode(codec.encode(expected))))
    }

    /** 同一玩家重複出現不得靜默採用最後一筆。 */
    @Test
    fun `duplicate entries and missing required fields are rejected`() {
        val table = Uuid.random()
        val player = Uuid.random()
        val choice = """{"playerId":"$player","characterId":"example:actor"}"""
        val codec = AiCharacterMetadataCodec()
        assertFailsWith<IllegalArgumentException> { codec.decode("""{"selections":[{"tableId":"$table","players":[$choice,$choice]}],"matches":[]}""") }
        assertFailsWith<IllegalArgumentException> { codec.decode("""{"selections":[]}""") }
        assertFailsWith<IllegalArgumentException> { codec.decode("""{"selections":[],"matches":[{"matchId":"$table","players":[{"playerId":"$player","characterId":"example:actor"}]}]}""") }
    }
}
