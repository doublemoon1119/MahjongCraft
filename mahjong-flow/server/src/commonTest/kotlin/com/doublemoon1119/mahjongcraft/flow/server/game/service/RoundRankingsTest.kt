package com.doublemoon1119.mahjongcraft.flow.server.game.service

import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** [roundRanksByPlayer] 依規則的回合排名決定名次。 */
class RoundRankingsTest {
    private val module = RiichiRuleModule("mahjongcraft:riichi", RiichiRuleConfig())

    /** 同分時依這一局的座位決定名次，不是起家時的座位。 */
    @Test
    fun `tied scores are ranked by this hand's seat, not the initial seat`() {
        val eastId = Uuid.random()
        val southId = Uuid.random()
        val westId = Uuid.random()
        val northId = Uuid.random()
        // 兩名同分玩家的起家座位與本局座位刻意相反；誤用起家座位時兩人的名次會對調。
        val state = FakeTableStateFactory.create(
            players = listOf(
                FakeMahjongPlayerFactory.create(id = eastId, initialSeat = Wind.EAST).copy(score = 25_000, seatWind = Wind.SOUTH),
                FakeMahjongPlayerFactory.create(id = southId, initialSeat = Wind.WEST).copy(score = 25_000, seatWind = Wind.EAST),
                FakeMahjongPlayerFactory.create(id = westId, initialSeat = Wind.SOUTH).copy(score = 30_000, seatWind = Wind.WEST),
                FakeMahjongPlayerFactory.create(id = northId, initialSeat = Wind.NORTH).copy(score = 20_000, seatWind = Wind.NORTH),
            ),
        )

        assertEquals(
            mapOf(westId to 1, southId to 2, eastId to 3, northId to 4),
            roundRanksByPlayer(state, module),
        )
    }
}
