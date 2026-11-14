package com.doublemoon1119.mahjongcraft.platform.fabric.text

import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftConfigCommandKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftServerConfig
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MissingAiPlayerPolicy
import net.minecraft.text.HoverEvent
import net.minecraft.text.Text
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

/** 伺服器設定的分類順序與獨立懸停訊息測試。 */
class ServerConfigChatFormattingTest {
    /** 分類依 TOML 順序排列，每項設定只出現一次。 */
    @Test
    fun `test sections follow toml order and contain all settings`() {
        val sections = serverConfigSections(MinecraftServerConfig())
        assertEquals(
            expected = listOf(
                MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_PLAYER_DISCONNECTION,
                MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_AI_PLAYER,
                MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_TABLE,
                MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_MAHJONG_TILE,
                MinecraftConfigCommandKeys.SERVER_CONFIG_SECTION_HISTORY,
            ),
            actual = sections.map { (it.name.content as TranslatableTextContent).key },
        )
        assertEquals(listOf(2, 1, 2, 1, 11), sections.map { it.entries.size })
        val keys = sections.flatMap { it.entries }.map { (it.name.content as TranslatableTextContent).key }
        assertEquals(keys.size, keys.distinct().size)
        assertEquals(1, keys.count { it == MinecraftConfigCommandKeys.HISTORY_QUERY_ENABLED })
        assertEquals(1, keys.count { it == MinecraftConfigCommandKeys.HISTORY_ALLOW_ADMIN_QUERY })
        assertEquals(1, keys.count { it == MinecraftConfigCommandKeys.HISTORY_QUERY_MINIMUM_INTERVAL })
        assertEquals(1, keys.count { it == MinecraftConfigCommandKeys.HISTORY_QUERY_MAX_OUTSTANDING })
        assertEquals(1, keys.count { it == MinecraftConfigCommandKeys.HISTORY_QUERY_REJECTION_REPLY_INTERVAL })
    }

    /** 單則訊息只有一個前綴，五個分類標籤各自擁有獨立懸停內容。 */
    @Test
    fun `test message contains five independent section tooltips`() {
        val message = serverConfigShowMessage("test/server.toml", MinecraftServerConfig())
        val labels = message.siblings.filter { it.style.hoverEvent != null }
        assertEquals(5, labels.size)
        val sections = serverConfigSections(MinecraftServerConfig())
        labels.zip(sections).forEach { (label, section) ->
            val hover = assertNotNull(label.style.hoverEvent).getValue(HoverEvent.Action.SHOW_TEXT)
            assertNotNull(hover)
            assertEquals((section.name.content as TranslatableTextContent).key, (hover.content as TranslatableTextContent).key)
            assertEquals(section.entries.size * 4, hover.siblings.single().siblings.size)
        }
        val header = message.siblings.first().content as TranslatableTextContent
        val pathLabel = header.args[1] as Text
        assertEquals("test/server.toml", assertNotNull(pathLabel.style.hoverEvent).getValue(HoverEvent.Action.SHOW_TEXT)?.string)
    }

    /** 遊戲內查詢直接顯示 AI 玩家政策，不輸出內部 enum 或程式名稱。 */
    @Test
    fun `AI player missing policy is shown once with a localized choice`() {
        for ((policy, key) in mapOf(MissingAiPlayerPolicy.ABORT_MATCH to MinecraftConfigCommandKeys.ABORT_MATCH, MissingAiPlayerPolicy.CONTINUE_MATCH to MinecraftConfigCommandKeys.CONTINUE_MATCH)) {
            val entries = serverConfigEntries(MinecraftServerConfig(missingAiPlayerPolicy = policy))
            val entry = entries.single { (it.name.content as TranslatableTextContent).key == MinecraftConfigCommandKeys.MISSING_AI_PLAYER_POLICY }
            assertEquals(key, (entry.displayedValue.content as TranslatableTextContent).key)
        }
    }
}
