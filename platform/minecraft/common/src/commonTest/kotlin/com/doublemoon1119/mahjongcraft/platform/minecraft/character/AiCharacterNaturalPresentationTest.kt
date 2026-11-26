package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterFace
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.STANDARD_TILE_ASSET_KEYS
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證內建造型與注視節奏只依公開身分，不使用牌局私有內容。 */
class AiCharacterNaturalPresentationTest {
    /** 造型候選只有標準公開牌面，同一場與身分的選擇可重現。 */
    @Test
    fun `mascot appearance uses standard faces and is reproducible for its match identity`() {
        val match = Uuid.parse("10000000-0000-0000-0000-000000000000")
        val player = Uuid.parse("20000000-0000-0000-0000-000000000000")
        val first = AiCharacterMatchAppearance.capture(BuiltInAiCharacters.TILE_MASCOT, match, player)
        assertEquals(first, AiCharacterMatchAppearance.capture(BuiltInAiCharacters.TILE_MASCOT, match, player))
        assertTrue((first?.face as? AiCharacterFace.Tile)?.assetKey in STANDARD_TILE_ASSET_KEYS)
        val faces = (0 until 128).map { index ->
            val member = Uuid.parse("20000000-0000-0000-0000-${index.toString().padStart(12, '0')}")
            AiCharacterMatchAppearance.capture(BuiltInAiCharacters.TILE_MASCOT, match, member)?.face
        }.toSet()
        assertTrue(faces.size > 1)
    }

    /** 已指定的第三方外觀不能被內建隨機機制覆寫。 */
    @Test
    fun `explicit third party appearance and unavailable definitions remain unchanged`() {
        val explicit = AiCharacterDefinition("example:actor", "example.actor", AiCharacterFace.Tile("east"), null, null)
        val match = Uuid.random()
        val player = Uuid.random()
        assertSame(explicit, AiCharacterMatchAppearance.capture(explicit, match, player))
        assertNull(AiCharacterMatchAppearance.capture(null, match, player))
    }

    /** 每個角色絕大部分時間以自己的手牌區為主，不長時間盯著同一名參與者。 */
    @Test
    fun `hand area is the dominant look focus`() {
        val player = Uuid.parse("20000000-0000-0000-0000-000000000001")
        val counts = (0L until 2000L).map { AiCharacterLookSchedule.focus(player, it) }.groupingBy { it }.eachCount()
        assertTrue(counts.getValue(AiCharacterLookFocus.HAND_AREA) > 1500)
        assertTrue(counts.getValue(AiCharacterLookFocus.DISCARD) > 0)
        assertTrue(counts.getValue(AiCharacterLookFocus.PARTICIPANT) > 0)
    }

    /** 角色身分不同時，偶爾轉看的時間不完全重合。 */
    @Test
    fun `different identities do not share one synchronized look schedule`() {
        val first = Uuid.parse("20000000-0000-0000-0000-000000000001")
        val second = Uuid.parse("20000000-0000-0000-0000-000000000200")
        assertTrue((0L until 1200L).any { AiCharacterLookSchedule.focus(first, it) != AiCharacterLookSchedule.focus(second, it) })
    }
}
