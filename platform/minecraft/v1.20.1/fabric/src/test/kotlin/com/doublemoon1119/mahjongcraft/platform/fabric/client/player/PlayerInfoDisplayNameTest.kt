package com.doublemoon1119.mahjongcraft.platform.fabric.client.player

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.MahjongPlayerInfoEntry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 面板只有需要角色翻譯的 AI 才使用本地名稱，保留已知名稱及真人路徑。 */
class PlayerInfoDisplayNameTest {
    /** 第三方翻譯名稱不顯示成離線玩家，普通機器人與真人不被回呼改名。 */
    @Test
    fun `only unresolved AI names invoke the character resolver`() {
        val actor = MahjongPlayerInfoEntry(
            playerId = Uuid.random(),
            playerName = null,
            isAi = true,
            seatIndex = 0,
            seatWind = Wind.EAST,
            score = 100,
            indicators = emptyList(),
        )
        assertEquals("Localized actor", playerInfoDisplayName(actor) { "Localized actor" })
        assertEquals("AI 1", playerInfoDisplayName(actor.copy(playerName = "AI 1")) { error("Numbered name must be preserved") })
        assertEquals("Named actor", playerInfoDisplayName(actor.copy(playerName = "Named actor")) { error("Explicit name must be preserved") })
        assertEquals("Player", playerInfoDisplayName(actor.copy(isAi = false, playerName = "Player")) { error("Human name must be preserved") })
    }
}
