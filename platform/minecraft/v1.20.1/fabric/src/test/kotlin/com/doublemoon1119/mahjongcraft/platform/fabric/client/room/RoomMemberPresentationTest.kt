package com.doublemoon1119.mahjongcraft.platform.fabric.client.room

import com.doublemoon1119.mahjongcraft.logic.module.PublicPlayerIndicator
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.client.render.PublicPlayerIndicatorTextResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import net.minecraft.text.TranslatableTextContent
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證成員卡片內容的組成與降級玩家清單。 */
class RoomMemberPresentationTest {
    private val presentation = RoomMemberPresentation(
        PublicPlayerIndicatorTextResolver(PublicPlayerIndicatorDisplayRegistryImpl()),
    )

    /** 非莊家的第一列只有自風。 */
    @Test
    fun `starts the rows with the seat wind`() {
        val rows = presentation.infoRows(entry(seatWind = Wind.SOUTH), dealerPlayerId = OTHER_PLAYER_ID)

        assertEquals(RoomMemberPresentation.WIND_COLOR, rows.first().second)
        val content = assertIs<TranslatableTextContent>(rows.first().first.content)
        assertEquals(MinecraftMessageKeys.TILE_HONOR_SOUTH, content.key)
    }

    /** 莊家的自風後方帶標記。 */
    @Test
    fun `marks the dealer after the seat wind`() {
        val rows = presentation.infoRows(entry(), dealerPlayerId = PLAYER_ID)

        assertContains(rows.first().first.string, RoomMemberPresentation.DEALER_MARKER)
    }

    /** 分數列使用千分位格式。 */
    @Test
    fun `formats the score with thousands separators`() {
        val rows = presentation.infoRows(entry(score = 25000), dealerPlayerId = null)

        assertEquals("25,000", rows[1].first.string)
        assertEquals(RoomMemberPresentation.SCORE_COLOR, rows[1].second)
    }

    /** 負分同樣套用千分位格式。 */
    @Test
    fun `formats a negative score`() {
        assertEquals("-1,200", formatRoomInteger(-1200))
    }

    /** 沒有指示器時只有自風與分數兩列。 */
    @Test
    fun `builds two rows without indicators`() {
        assertEquals(2, presentation.infoRows(entry(), dealerPlayerId = null).size)
    }

    /** 指示器接在分數之後，依原順序排列。 */
    @Test
    fun `appends every indicator after the score`() {
        val rows = presentation.infoRows(
            entry(indicators = listOf(PublicPlayerIndicator("mahjongcraft:riichi"), PublicPlayerIndicator("mahjongcraft:furiten"))),
            dealerPlayerId = null,
        )

        assertEquals(4, rows.size)
        assertEquals("mahjongcraft:riichi", rows[2].first.string)
        assertEquals("mahjongcraft:furiten", rows[3].first.string)
    }

    /** 四個自風各自對應到自己的翻譯 key。 */
    @Test
    fun `maps every seat wind to its own key`() {
        val keys = Wind.entries.map { wind ->
            assertIs<TranslatableTextContent>(RoomMemberPresentation.windText(wind).content).key
        }

        assertEquals(keys.toSet().size, keys.size, "Expected every seat wind to use its own translation key.")
        assertEquals(MinecraftMessageKeys.TILE_HONOR_EAST, keys[Wind.EAST.ordinal])
    }

    /** 降級清單依座位順序保留自風與分數。 */
    @Test
    fun `keeps seat order and scores in the fallback roster`() {
        val entries = roomMemberEntriesFrom(
            listOf(snapshot(Wind.EAST, score = 30000), snapshot(Wind.SOUTH, score = 20000)),
            resolveHumanName = { "Player" },
        )

        assertEquals(listOf(0, 1), entries.map { it.seatIndex })
        assertEquals(listOf(Wind.EAST, Wind.SOUTH), entries.map { it.seatWind })
        assertEquals(listOf(30000, 20000), entries.map { it.score })
    }

    /** 真人玩家的名稱交由呼叫端解析，解析不到時保留 null。 */
    @Test
    fun `leaves an unresolved human name null`() {
        val entries = roomMemberEntriesFrom(listOf(snapshot(Wind.EAST)), resolveHumanName = { null })

        assertNull(entries.single().playerName)
    }

    /** AI 玩家不經過真人名稱解析，並取得依序產生的顯示名稱。 */
    @Test
    fun `names ai players without the human resolver`() {
        val entries = roomMemberEntriesFrom(
            listOf(snapshot(Wind.EAST, isAi = true), snapshot(Wind.SOUTH, isAi = true)),
            resolveHumanName = { error("AI players must not use the human name resolver") },
        )

        assertEquals(2, entries.count { it.isAi })
        assertEquals(2, entries.mapNotNull { it.playerName }.toSet().size, "Expected the two AI players to get different display names.")
    }

    /** 降級清單不含規則公開指示器。 */
    @Test
    fun `carries no indicators in the fallback roster`() {
        val entries = roomMemberEntriesFrom(listOf(snapshot(Wind.EAST)), resolveHumanName = { "Player" })

        assertEquals(emptyList(), entries.single().indicators)
    }

    /** 建立測試用的資訊列來源。 */
    private fun entry(
        seatWind: Wind = Wind.EAST,
        score: Int = 25000,
        indicators: List<PublicPlayerIndicator> = emptyList(),
    ) = MahjongPlayerInfoEntry(
        playerId = PLAYER_ID,
        playerName = "Player",
        isAi = false,
        seatIndex = seatWind.ordinal,
        seatWind = seatWind,
        score = score,
        indicators = indicators,
    )

    /** 建立測試用的玩家快照。 */
    private fun snapshot(seatWind: Wind, score: Int = 25000, isAi: Boolean = false) = FakeMahjongPlayerFactory.create(
        initialSeat = seatWind,
        aiStrategyKey = if (isAi) "mahjongcraft:random" else null,
    ).copy(score = score).toSnapshot(isVisible = true, revealsClosedKanTiles = false)

    private companion object {
        /** 測試用的玩家 ID。 */
        val PLAYER_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000001")

        /** 另一位測試用玩家的 ID。 */
        val OTHER_PLAYER_ID: Uuid = Uuid.parse("00000000-0000-0000-0000-000000000002")
    }
}
