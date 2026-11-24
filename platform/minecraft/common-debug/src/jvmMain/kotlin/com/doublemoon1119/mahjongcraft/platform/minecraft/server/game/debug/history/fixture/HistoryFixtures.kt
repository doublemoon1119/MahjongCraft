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

    /**
     * 日麻四人東風戰：同一局依序出現暗槓、碰、吃、加槓與明槓，最後明槓的人摸到嶺上牌自摸（嶺上開花）。
     *
     * 東家第一巡暗槓九萬，嶺上摸到東打出，北家碰；南家摸到三萬打出，西家吃；北家摸到第四張東加槓，嶺上摸到二索打出，南家以三張
     * 二索明槓，嶺上摸到七索自摸。
     */
    val RIICHI_CALLS_AND_KANS = HistoryFixture(
        id = "mahjongcraft:riichi/calls_and_kans",
        scenario = HeadlessHistoryScenario.RIICHI_EAST,
        rounds = listOf(
            element = HistoryFixtureRound(
                dice = listOf(3, 5),
                hands = mapOf(
                    Wind.EAST to "9999m 147p 158s 346z",
                    Wind.SOUTH to "222s 345m 678m 345p 7s",
                    Wind.WEST to "45m 1s 269p 369s 55z 77z",
                    Wind.NORTH to "11z 9s 147m 258p 48s 34z",
                ),
                draws = "2z 6z 3m 1z",
                reserved = mapOf(0 to "1z", 1 to "2s", 2 to "7s"),
                steps = listOf(
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.ClosedKan(tile("9m"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Pon(tile("1z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Discard(tile("9s"))),
                    HistoryFixtureStep(Wind.WEST, HistoryFixtureAction.Chi(HistoryFixtureTile.parse("45m"))),
                    HistoryFixtureStep(Wind.WEST, HistoryFixtureAction.Discard(tile("1s"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.AddedKan(tile("1z"))),
                    HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.OpenKan(tile("2s"))),
                    HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.Tsumo),
                ),
            ),
        ),
    )

    /**
     * 日麻四人東風戰：第一局東家第一巡打出九筒，南家與西家同時榮和（雙響）；第二局莊家第一次摸牌後宣告九種九牌流局；第三局北家
     * 依序碰東家打出的白、發、中，自摸大三元，打出最後一張三元牌的東家負責全額（包牌）。
     *
     * 包牌的東家獨自支付役滿而被擊飛，整場在第三局結束。
     */
    val RIICHI_DOUBLE_RON_PAO_KYUUSHU = HistoryFixture(
        id = "mahjongcraft:riichi/double_ron_pao_kyuushu",
        scenario = HeadlessHistoryScenario.RIICHI_EAST,
        rounds = listOf(
            HistoryFixtureRound(
                dice = listOf(4, 2),
                hands = mapOf(
                    Wind.EAST to "9p 147m 258s 36p 1234z",
                    Wind.SOUTH to "555z 234m 345p 678s 9p",
                    Wind.WEST to "666z 123m 456p 789s 9p",
                    Wind.NORTH to "369m 147s 28p 12347z",
                ),
                draws = "1s",
                steps = listOf(
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.Discard(tile("9p"))),
                    HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.Ron),
                    HistoryFixtureStep(Wind.WEST, HistoryFixtureAction.Ron),
                ),
            ),
            HistoryFixtureRound(
                dice = listOf(1, 3),
                hands = mapOf(
                    Wind.EAST to "19m 19p 1s 12345z 258m",
                    Wind.SOUTH to "34m 67p 789s 66z 77z 11p",
                    Wind.WEST to "67m 23p 456s 34z 9m 9p 5s 2s",
                    Wind.NORTH to "12m 38p 27s 8m 9s 12567z",
                ),
                draws = "5p",
                steps = listOf(element = HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.DeclareExhaustiveDraw)),
            ),
            HistoryFixtureRound(
                dice = listOf(5, 6),
                hands = mapOf(
                    Wind.EAST to "567z 147m 258p 369s 2z",
                    Wind.SOUTH to "258m 369p 147s 2344z",
                    Wind.WEST to "369m 147p 258s 123z 9m",
                    Wind.NORTH to "556677z 123m 4p 19s 1z",
                ),
                draws = "8m 8p 8s 9p 1p 7s 4p",
                steps = listOf(
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.Discard(tile("5z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Pon(tile("5z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Discard(tile("1s"))),
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.Discard(tile("6z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Pon(tile("6z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Discard(tile("9s"))),
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.Discard(tile("7z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Pon(tile("7z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Discard(tile("1z"))),
                    HistoryFixtureStep(Wind.NORTH, HistoryFixtureAction.Tsumo),
                ),
            ),
        ),
    )

    /** 日麻三人東風戰：東家第一巡拔北，嶺上補到的牌打出；南家摸到九萬，以東的刻子單騎自摸，由另外兩家付款。 */
    val THREE_PLAYER_RIICHI_PULL_NORTH_TSUMO = HistoryFixture(
        id = "mahjongcraft:riichi_three_player/pull_north_tsumo",
        scenario = HeadlessHistoryScenario.THREE_PLAYER_RIICHI_EAST,
        rounds = listOf(
            element = HistoryFixtureRound(
                dice = listOf(2, 6),
                hands = mapOf(
                    Wind.EAST to "4z 19m 147p 258s 1236z",
                    Wind.SOUTH to "123p 456p 789s 111z 9m",
                    Wind.WEST to "258p 369s 2357z 9m 1m 1s",
                ),
                draws = "8p 9m",
                reserved = mapOf(0 to "6p"),
                steps = listOf(
                    HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.PullNorth),
                    HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.Tsumo),
                ),
            ),
        ),
    )

    /** 台灣麻將：南家配牌有春，開局補花；南家第一次摸牌摸到夏，再補花一次；西家摸到九索，以白的刻子單騎自摸。 */
    private val TAIWAN_FLOWERS_TSUMO_ROUND = HistoryFixtureRound(
        dice = listOf(2, 3, 4),
        hands = mapOf(
            Wind.EAST to "147m 258p 369s 1234z 2m 7p 1s",
            Wind.SOUTH to "1f 147p 258s 369m 23467z 8m",
            Wind.WEST to "555z 123m 456m 789p 234s 9s",
            Wind.NORTH to "369p 147s 258m 123467z 6p",
        ),
        draws = "8s 2f 9s",
        steps = listOf(element = HistoryFixtureStep(Wind.WEST, HistoryFixtureAction.Tsumo)),
    )

    /** 台灣麻將：東家第一巡打出九萬，南家以發的刻子單騎榮和。 */
    private val TAIWAN_RON_ROUND = HistoryFixtureRound(
        dice = listOf(5, 1, 3),
        hands = mapOf(
            Wind.EAST to "9m 147p 258s 147m 1234z 5z 7z",
            Wind.SOUTH to "666z 123m 456p 789s 234p 9m",
            Wind.WEST to "258m 369p 147s 2345z 7z 3m 8p",
            Wind.NORTH to "369m 258p 369s 12347z 1s 4s",
        ),
        draws = "5s",
        steps = listOf(
            HistoryFixtureStep(Wind.EAST, HistoryFixtureAction.Discard(tile("9m"))),
            HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.Ron),
        ),
    )

    /**
     * 台灣麻將東風圈：第一局補花後閒家自摸，第二局閒家榮和，第三局四家都只打出剛摸到的牌，打到流局並由莊家連莊；之後兩局再由閒家
     * 自摸與榮和，讓莊家輪完一圈結束整場。
     *
     * 台灣麻將流局時莊家連莊，四家都只打出剛摸到的牌無法結束整場，因此每一局都照腳本進行。
     */
    val TAIWAN_FLOWERS_TSUMO_RON_DRAW = HistoryFixture(
        id = "mahjongcraft:taiwan/flowers_tsumo_ron_draw",
        scenario = HeadlessHistoryScenario.TAIWAN_EAST,
        rounds = listOf(
            TAIWAN_FLOWERS_TSUMO_ROUND,
            TAIWAN_RON_ROUND,
            TAIWAN_RON_ROUND.copy(steps = emptyList()),
            TAIWAN_FLOWERS_TSUMO_ROUND,
            TAIWAN_RON_ROUND,
        ),
    )

    /** 台灣麻將特殊花牌和牌兩局共用的配牌：南家配牌有春到菊七張花牌。 */
    private val TAIWAN_SEVEN_FLOWERS_HANDS = mapOf(
        Wind.EAST to "147m 258p 369s 1234z 2m 7p 1s",
        Wind.SOUTH to "1234567f 123m 456p 789s",
        Wind.WEST to "555z 123m 456m 789p 234s 9s",
        Wind.NORTH to "369p 147s 258m 123467z 6p",
    )

    /**
     * 台灣麻將東風圈：第一局南家自己摸到第八張花牌竹，集滿八張花牌和牌（八仙過海）；第二局西家摸到竹，有七張花牌的南家搶這張
     * 和牌（七搶一）；之後兩局由閒家自摸與榮和，讓莊家輪完一圈結束整場。
     *
     * 台灣麻將流局時莊家連莊，四家都只打出剛摸到的牌無法結束整場，因此每一局都照腳本進行。
     */
    val TAIWAN_EIGHT_FLOWERS_AND_ROBBING = HistoryFixture(
        id = "mahjongcraft:taiwan/eight_flowers_and_robbing",
        scenario = HeadlessHistoryScenario.TAIWAN_EAST,
        rounds = listOf(
            HistoryFixtureRound(
                dice = listOf(6, 2, 1),
                hands = TAIWAN_SEVEN_FLOWERS_HANDS,
                draws = "8s 8f",
                steps = listOf(element = HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.AcceptSpecialFlowerWin)),
            ),
            HistoryFixtureRound(
                dice = listOf(3, 3, 4),
                hands = TAIWAN_SEVEN_FLOWERS_HANDS,
                draws = "8s 5s 8f",
                steps = listOf(element = HistoryFixtureStep(Wind.SOUTH, HistoryFixtureAction.AcceptSpecialFlowerWin)),
            ),
            TAIWAN_FLOWERS_TSUMO_ROUND,
            TAIWAN_RON_ROUND,
        ),
    )

    /** 全部內建的測試牌譜。 */
    val ALL: List<HistoryFixture> = listOf(
        RIICHI_PON_RIICHI_IPPATSU_TSUMO,
        RIICHI_RON_THEN_EXHAUSTIVE_DRAW,
        RIICHI_CALLS_AND_KANS,
        RIICHI_DOUBLE_RON_PAO_KYUUSHU,
        THREE_PLAYER_RIICHI_PULL_NORTH_TSUMO,
        TAIWAN_FLOWERS_TSUMO_RON_DRAW,
        TAIWAN_EIGHT_FLOWERS_AND_ROBBING,
    )

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
