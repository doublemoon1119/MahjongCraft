package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 本局自動操作驗收用的日麻 debug 情境。
 *
 * 全部沿用 [SeatSetupScenario] 的座位設定與排定摸打。情境只負責造出局面，不預先啟用任何控制：
 * 啟用走正式路徑（設定畫面或客戶端指令），因此同一個情境可以同時觀察控制開啟與關閉的差異。
 * 呼叫者預先有兩張捨牌，牌局不在第一巡，且捨牌中沒有自己的和牌張，不會因振聽而失去和牌選項。
 */
object RiichiAutomaticControlDebugGameScenarios {
    /** 所有自動操作驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        SeatSetupScenario(
            id = "mahjongcraft:automatic_auto_win_tsumo",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_TWO_SIDED_WAIT,
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South, Tile.Honor.North),
            ),
            liveWallFront = listOf(NINE_BAMBOO, NINE_CHARACTER, Tile.Honor.North, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:automatic_auto_win_ron",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_TWO_SIDED_WAIT,
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South, Tile.Honor.North),
            ),
            liveWallFront = listOf(FOUR_CHARACTER, NINE_BAMBOO, NINE_CHARACTER, Tile.Honor.North),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:automatic_decline_calls_keeps_win",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_SHANPON_WAIT,
                ponTiles = listOf(NINE_CHARACTER),
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South, Tile.Honor.North),
            ),
            liveWallFront = listOf(Tile.Numeric(Tile.Suit.Bamboo, 2), NINE_BAMBOO, NINE_CHARACTER, Tile.Honor.North),
            waitingTiles = SHANPON_WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:automatic_tsumogiri_plain_draw",
            invoker = SeatSpec(
                standingTiles = FAR_FROM_TENPAI,
                drawnTile = NINE_BAMBOO,
                discards = listOf(Tile.Honor.South, Tile.Honor.North),
            ),
            liveWallFront = listOf(Tile.Honor.North, Tile.Honor.South, Tile.Honor.West),
            waitingTiles = emptySet(),
        ),
    )
}

/**
 * 四五六筒、中中中、二二條、三三條：雙碰聽二、三條，中的役牌役不依賴和牌張，二條同時可碰。
 *
 * 搭配一組已碰出的副露使用，因此呼叫者不是門前狀態，載入後不會被提供立直——立直之後就不能碰，
 * 這個情境要觀察的「碰被拿掉、榮和留著」也就無從觀察。
 */
private val YAKUHAI_SHANPON_WAIT: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Dot, 4),
    Tile.Numeric(Tile.Suit.Dot, 5),
    Tile.Numeric(Tile.Suit.Dot, 6),
    Tile.Honor.Red,
    Tile.Honor.Red,
    Tile.Honor.Red,
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Numeric(Tile.Suit.Bamboo, 3),
    Tile.Numeric(Tile.Suit.Bamboo, 3),
)

/** 雙碰情境的和牌張。 */
private val SHANPON_WAITING_TILES: Set<Tile> = setOf(
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Numeric(Tile.Suit.Bamboo, 3),
)

/** 二三四萬、五六七萬、二三四筒、五六筒、二三條：沒有對子因此未聽牌，也沒有任何可槓的牌。 */
private val FAR_FROM_TENPAI: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 2),
    Tile.Numeric(Tile.Suit.Character, 3),
    Tile.Numeric(Tile.Suit.Character, 4),
    Tile.Numeric(Tile.Suit.Character, 5),
    Tile.Numeric(Tile.Suit.Character, 6),
    Tile.Numeric(Tile.Suit.Character, 7),
    Tile.Numeric(Tile.Suit.Dot, 2),
    Tile.Numeric(Tile.Suit.Dot, 3),
    Tile.Numeric(Tile.Suit.Dot, 4),
    Tile.Numeric(Tile.Suit.Dot, 5),
    Tile.Numeric(Tile.Suit.Dot, 6),
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Numeric(Tile.Suit.Bamboo, 3),
)
