package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

/** 驗證玩家資訊同步快照使用穩定名稱傳遞座位風位。 */
class PlayerEntryDtoTest {
    @Test
    fun `seat wind round trips by stable name`() {
        val original = PlayerEntryDto(
            playerId = "00000000-0000-0000-0000-000000000001",
            playerName = "Alice",
            isAi = false,
            seatIndex = 0,
            seatWind = Wind.SOUTH.name,
            score = 25000,
            indicators = emptyList(),
        )

        val domain = assertNotNull(original.toDomain())
        assertEquals(Wind.SOUTH, domain.seatWind)
        assertEquals(Wind.SOUTH.name, PlayerEntryDto.fromDomain(domain).seatWind)
    }

    @Test
    fun `unknown seat wind is discarded instead of falling back to another wind`() {
        val malformed = PlayerEntryDto(
            playerId = "00000000-0000-0000-0000-000000000001",
            playerName = "Alice",
            isAi = false,
            seatIndex = 0,
            seatWind = "INVALID",
            score = 25000,
            indicators = emptyList(),
        )

        assertNull(malformed.toDomain())
    }

    @Test
    fun `malformed indicator is discarded with its player entry`() {
        val malformed = PlayerEntryDto(
            playerId = "00000000-0000-0000-0000-000000000001",
            playerName = "Alice",
            isAi = false,
            seatIndex = 0,
            seatWind = Wind.EAST.name,
            score = 25000,
            indicators = listOf(IndicatorDto("wins", "count", "not-a-number")),
        )

        assertNull(malformed.toDomain())
    }
}
