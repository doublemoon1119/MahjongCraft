package com.doublemoon1119.mahjongcraft.example.minecraft.mod.event

import com.doublemoon1119.mahjongcraft.example.minecraft.mod.ExampleMinecraftTestBootstrap
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat.administratorRecipients
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat.eventMessage
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat.formatPlayerLabels
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat.locationText
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat.playerValue
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat.scoreChangeValue
import com.doublemoon1119.mahjongcraft.example.minecraft.mod.event.ExampleGameEventChat.standingValue
import com.doublemoon1119.mahjongcraft.example.minecraft.text.ExampleEventMessageKeys
import net.minecraft.ChatFormatting
import net.minecraft.network.chat.Component
import net.minecraft.network.chat.HoverEvent
import net.minecraft.network.chat.contents.TranslatableContents
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 範例事件聊天觀察器的接收者篩選測試。 */
class ExampleGameEventChatTest {
    /** 懸停文字會用到原版登記表。 */
    @BeforeTest
    fun bootstrap() {
        ExampleMinecraftTestBootstrap.ensure()
    }

    /** 事件摘要以 AQUA 色中括號標籤顯示，文字與括號都有相同懸停內容。 */
    @Test
    fun `event summary stays on one line and hover keeps details`() {
        val message = eventMessage(Component.literal("Round 1 settled"), Component.literal("event details"))

        assertEquals("[Round 1 settled]", message.string)
        (listOf(message) + message.siblings).forEach { part ->
            assertEquals(ChatFormatting.AQUA.color, part.style.color?.value)
            assertEquals(
                expected = "event details",
                actual = assertIs<Component>(part.style.hoverEvent?.getValue(HoverEvent.Action.SHOW_TEXT)).string,
            )
            assertEquals(null, part.style.clickEvent)
        }
    }

    /** 只有管理員權限的玩家可接收事件診斷訊息。 */
    @Test
    fun `event recipient requires administrator permission`() {
        assertEquals(listOf("Admin"), administratorRecipients(listOf("Player", "Admin")) { it == "Admin" })
        assertTrue(administratorRecipients(listOf("Player")) { false }.isEmpty())
        assertTrue(administratorRecipients(emptyList<String>()) { true }.isEmpty())
    }

    /** 位置缺失與玩家名稱遺失都保留可讀的識別資訊。 */
    @Test
    fun `formatter keeps unknown location and stable player identifiers`() {
        val first = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val second = Uuid.parse("00000000-0000-0000-0000-000000000002")

        assertEquals(ExampleEventMessageKeys.UNKNOWN_LOCATION, assertIs<TranslatableContents>(locationText(null).contents).key)
        assertEquals(
            expected = listOf("Alice ($first)", "$second ($second)"),
            actual = formatPlayerLabels(setOf(second, first)) { id -> if (id == first) "Alice" else id.toString() },
        )
    }

    /** 玩家欄位保留完整參數，離線名稱與在線名稱都不會吃掉其他欄位。 */
    @Test
    fun `player line keeps every translation argument and uuid fallback`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val offline = assertIs<TranslatableContents>(playerValue(id, true, 2).contents)
        assertEquals(ExampleEventMessageKeys.PLAYER, offline.key)
        assertEquals(4, offline.args.size)
        assertEquals(id.toString(), assertIs<Component>(offline.args[0]).string)
        assertEquals(id.toString(), offline.args[1])
        assertEquals("true", offline.args[2])
        assertEquals(2, offline.args[3])
        val online = assertIs<TranslatableContents>(playerValue(id, false, 1, Component.literal("Alice")).contents)
        assertEquals("Alice", assertIs<Component>(online.args[0]).string)
        assertEquals(id.toString(), online.args[1])
    }

    /** 共通事件欄位包含所有識別碼，位置缺失時仍使用未知位置翻譯鍵。 */
    @Test
    fun `event line keeps identifiers and unknown location`() {
        val eventId = Uuid.parse("00000000-0000-0000-0000-000000000001")
        val matchId = Uuid.parse("00000000-0000-0000-0000-000000000002")
        val venueId = Uuid.parse("00000000-0000-0000-0000-000000000003")
        val content = ExampleGameEventChat.eventText(
            eventId = eventId,
            matchId = matchId,
            venueId = venueId,
            ruleModuleId = "example:pairing",
            tableLocation = null,
            title = ExampleEventMessageKeys.MATCH_STARTED,
        )

        val fields = content.siblings.mapNotNull { it.siblings.singleOrNull()?.contents as? TranslatableContents }
        assertEquals(eventId.toString(), fields.single { it.key == ExampleEventMessageKeys.EVENT_ID }.args.single())
        assertEquals(matchId.toString(), fields.single { it.key == ExampleEventMessageKeys.MATCH_ID }.args.single())
        assertEquals(venueId.toString(), fields.single { it.key == ExampleEventMessageKeys.VENUE_ID }.args.single())
        assertEquals("example:pairing", fields.single { it.key == ExampleEventMessageKeys.RULE_MODULE_ID }.args.single())
    }

    /** 結算與終局欄位保留分數、名次、AI 身分與 UUID。 */
    @Test
    fun `settlement and standing lines keep every value`() {
        val id = Uuid.parse("00000000-0000-0000-0000-000000000004")
        val score = assertIs<TranslatableContents>(
            value = scoreChangeValue(id, 100, 120, 2, 1, Component.literal("Alice")).contents,
        )
        assertEquals(ExampleEventMessageKeys.SCORE_CHANGE, score.key)
        assertEquals("Alice", assertIs<Component>(score.args[0]).string)
        assertEquals(id.toString(), score.args[1])
        assertEquals(100, score.args[2])
        assertEquals(120, score.args[3])
        assertEquals(2, score.args[4])
        assertEquals(1, score.args[5])

        val standing = assertIs<TranslatableContents>(standingValue(id, true, 120, 1).contents)
        assertEquals(ExampleEventMessageKeys.STANDING, standing.key)
        assertEquals(id.toString(), assertIs<Component>(standing.args[0]).string)
        assertEquals(id.toString(), standing.args[1])
        assertEquals("true", standing.args[2])
        assertEquals(120, standing.args[3])
        assertEquals(1, standing.args[4])
    }
}
