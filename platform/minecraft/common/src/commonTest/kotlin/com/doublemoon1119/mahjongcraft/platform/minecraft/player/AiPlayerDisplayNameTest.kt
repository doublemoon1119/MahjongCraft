package com.doublemoon1119.mahjongcraft.platform.minecraft.player

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [aiPlayerDisplayName] 的穩定 AI 順序測試。 */
class AiPlayerDisplayNameTest {
    /** 顯示名稱依 AI 專用順序編號，而非包含一般玩家的全體順序。 */
    @Test
    fun `test AI names use AI-only order`() {
        val first = Uuid.random()
        val second = Uuid.random()

        assertEquals("AI 1", aiPlayerDisplayName(first, listOf(first, second)))
        assertEquals("AI 2", aiPlayerDisplayName(second, listOf(first, second)))
    }

    /** 缺少 AI 順序資料時，以 UUID 前綴產生穩定名稱。 */
    @Test
    fun `test missing AI order uses stable UUID fallback`() {
        val playerId = Uuid.parse("12345678-1234-1234-1234-123456789abc")

        assertEquals("AI-123456", aiPlayerDisplayName(playerId, emptyList()))
    }

    /** 具名實體可出現在名冊任何位置，均不占用機器人的序號。 */
    @Test
    fun `named actors do not consume bot ordinals in any roster position`() {
        val actor = Uuid.random()
        val first = Uuid.random()
        val second = Uuid.random()
        val appearances = mapOf(actor to AiCharacterSnapshot("example:actor", null, "Actor"))
        val orders = listOf(listOf(actor, first, second), listOf(first, actor, second), listOf(first, second, actor))

        orders.forEach { order ->
            assertEquals("Actor", aiPlayerDisplayName(actor, order, appearances))
            assertEquals("AI 1", aiPlayerDisplayName(first, order, appearances))
            assertEquals("AI 2", aiPlayerDisplayName(second, order, appearances))
        }
    }

    /** 使用自己翻譯名稱的角色不占序號，內建預設外觀仍使用編號。 */
    @Test
    fun `translated character types do not consume default bot ordinals`() {
        val actor = Uuid.random()
        val bot = Uuid.random()
        val definition = AiCharacterDefinition("example:actor", "example.actor", null, null, null)
        val appearances = mapOf(
            actor to AiCharacterSnapshot(definition.id, definition),
            bot to AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, BuiltInAiCharacters.TILE_MASCOT),
        )

        assertEquals("AI 1", aiPlayerDisplayName(bot, listOf(actor, bot), appearances))
    }

    /** 命名的內建角色也不占序號；沒有描述時維持名冊編號，不猜測身分。 */
    @Test
    fun `custom names override default appearances and absent metadata preserves numbering`() {
        val actor = Uuid.random()
        val bot = Uuid.random()
        val order = listOf(actor, bot)
        val appearances = mapOf(actor to AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, BuiltInAiCharacters.TILE_MASCOT, "Renamed bot"))

        assertEquals("Renamed bot", aiPlayerDisplayName(actor, order, appearances))
        assertEquals("AI 1", aiPlayerDisplayName(bot, order, appearances))
        assertEquals("AI 2", aiPlayerDisplayName(bot, order))
        assertEquals("AI 1", aiPlayerDisplayName(bot, listOf(bot), appearances))
    }
}
