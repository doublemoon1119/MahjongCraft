package com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.history.fixture

import com.doublemoon1119.mahjongcraft.flow.server.game.history.generation.HeadlessHistoryScenario
import com.doublemoon1119.mahjongcraft.logic.table.Wind

/** 內建的測試牌譜，每一份涵蓋幾種要在牌譜與回放中檢查的情況。 */
object HistoryFixtures {
    /**
     * 日麻四人東風戰：東家第一巡打出中，北家碰；南家立直後一巡內自摸（一發）。
     *
     * 南家配牌是一氣通貫的單騎聽牌，摸到九索時立直打出，第四次摸牌摸到一索自摸。
     */
    val RIICHI_PON_RIICHI_IPPATSU_TSUMO = HistoryFixture(
        id = "mahjongcraft:riichi/pon_riichi_ippatsu_tsumo",
        scenario = HeadlessHistoryScenario.RIICHI_EAST,
        rounds = listOf(
            element = HistoryFixtureRound(
                dice = listOf(3, 4),
                hands = mapOf(
                    Wind.EAST to "7z 147p 258s 369m 123z",
                    Wind.SOUTH to "123456789m 234p 1s",
                    Wind.WEST to "1369p 247s 258m 356z",
                    Wind.NORTH to "77z 258p 369s 47m 124z",
                ),
                draws = "8m 8s 9s 2p 7p 3p 1s",
                steps = listOf(
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.Discard(tile("7z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Pon(tile("7z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Discard(tile("1z"))),
                    HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.Riichi(tile("9s"))),
                    HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.Tsumo),
                ),
            ),
        ),
    )

    /**
     * 日麻四人東風戰：第一局東家第一巡打出九筒，西家以白的刻子單騎榮和；第二局四家都只打出剛摸到的牌，打到流局，莊家聽牌收取罰符。
     */
    val RIICHI_RON_THEN_EXHAUSTIVE_DRAW = HistoryFixture(
        id = "mahjongcraft:riichi/ron_then_exhaustive_draw",
        scenario = HeadlessHistoryScenario.RIICHI_EAST,
        rounds = listOf(
            HistoryFixtureRound(
                dice = listOf(2, 5),
                hands = mapOf(
                    Wind.EAST to "9p 147m 147s 258p 123z",
                    Wind.SOUTH to "258m 258s 369p 4671z",
                    Wind.WEST to "555z 234m 345p 678s 9p",
                    Wind.NORTH to "369m 369s 147p 2347z",
                ),
                draws = "6z",
                steps = listOf(
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.Discard(tile("9p"))),
                    HistoryFixtureStep(Wind.WEST, HistoryFixtureAction.Ron),
                ),
            ),
            HistoryFixtureRound(
                dice = listOf(4, 4),
                hands = mapOf(
                    Wind.EAST to "123m 456p 789s 234s 6z",
                    Wind.SOUTH to "147m 258p 369s 1234z",
                    Wind.WEST to "258m 369p 147s 1235z",
                    Wind.NORTH to "369m 147p 258s 4577z",
                ),
                draws = "1p 2p 3p 4p",
            ),
        ),
    )

    /** 全部內建的測試牌譜。 */
    val ALL: List<HistoryFixture> = listOf(RIICHI_PON_RIICHI_IPPATSU_TSUMO, RIICHI_RON_THEN_EXHAUSTIVE_DRAW)

    /**
     * 依識別碼找測試牌譜。
     *
     * @param id 測試牌譜的識別碼。
     * @return 測試牌譜；沒有這個識別碼時為 null。
     */
    fun find(id: String): HistoryFixture? = ALL.firstOrNull { it.id == id }

    /**
     * 單張牌的寫法。
     *
     * @param notation 一張牌的寫法。
     * @return 這張牌。
     */
    private fun tile(notation: String): HistoryFixtureTile = HistoryFixtureTile.parse(notation).single()
}
