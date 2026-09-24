package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MultiRonPolicy
import com.doublemoon1119.mahjongcraft.logic.config.RonResolution

/**
 * 振聽驗收用的日麻 debug 情境。
 *
 * 全部沿用 [SeatSetupScenario] 的座位設定與排定摸打；呼叫者預先有兩張捨牌，牌局因此不在第一巡，
 * 不會觸發第一巡相關的役與流局。
 */
object RiichiFuritenDebugGameScenarios {
    /** 所有振聽驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        SeatSetupScenario(
            id = "mahjongcraft:riichi_furiten_discarded_wait",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_TWO_SIDED_WAIT,
                drawnTile = Tile.Honor.West,
                discards = listOf(ONE_CHARACTER, Tile.Honor.South),
            ),
            liveWallFront = listOf(FOUR_CHARACTER, Tile.Honor.North, NINE_BAMBOO, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:riichi_furiten_unoffered_wait",
            invoker = SeatSpec(
                standingTiles = TANYAO_ONLY_ON_FOUR_WAIT,
                drawnTile = Tile.Honor.North,
                discards = listOf(Tile.Honor.South, Tile.Honor.West),
            ),
            liveWallFront = listOf(ONE_CHARACTER, FOUR_CHARACTER, Tile.Honor.East, NINE_BAMBOO, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:riichi_furiten_declined_ron",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_TWO_SIDED_WAIT,
                drawnTile = Tile.Honor.North,
                discards = listOf(Tile.Honor.South, Tile.Honor.West),
            ),
            liveWallFront = listOf(ONE_CHARACTER, FOUR_CHARACTER, NINE_BAMBOO, NINE_CHARACTER, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:riichi_furiten_cleared_by_call",
            invoker = SeatSpec(
                standingTiles = RED_DRAGON_PAIR_BEFORE_CALL,
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South, NINE_BAMBOO),
            ),
            liveWallFront = listOf(ONE_CHARACTER, Tile.Honor.Red, ONE_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:riichi_furiten_declined_chankan",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_TWO_SIDED_WAIT,
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South),
                riichiTile = Tile.Honor.North,
            ),
            downstream = SeatSpec(ponTiles = listOf(ONE_CHARACTER), strategyKey = DebugScriptedAiStrategy.KAN_FIRST_KEY),
            liveWallFront = listOf(ONE_CHARACTER, FOUR_CHARACTER, NINE_BAMBOO, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:riichi_furiten_closed_kan_not_offered",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_TWO_SIDED_WAIT,
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South, NINE_BAMBOO),
            ),
            downstream = SeatSpec(
                standingTiles = List(3) { ONE_CHARACTER },
                strategyKey = DebugScriptedAiStrategy.KAN_FIRST_KEY,
            ),
            liveWallFront = listOf(ONE_CHARACTER, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        SeatSetupScenario(
            id = "mahjongcraft:riichi_furiten_head_bump_chankan",
            invoker = SeatSpec(
                standingTiles = YAKUHAI_TWO_SIDED_WAIT,
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South, NINE_BAMBOO),
            ),
            downstream = SeatSpec(ponTiles = listOf(ONE_CHARACTER), strategyKey = DebugScriptedAiStrategy.KAN_FIRST_KEY),
            across = SeatSpec(standingTiles = WHITE_DRAGON_TWO_SIDED_WAIT),
            liveWallFront = listOf(ONE_CHARACTER, FOUR_CHARACTER, NINE_CHARACTER, Tile.Honor.North, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
            multiRonPolicy = MultiRonPolicy(
                doubleRonResolution = RonResolution.NEAREST_WINNER,
                tripleRonResolution = RonResolution.NEAREST_WINNER,
            ),
        ),
    )
}

/** 二三萬、四五六筒、七七七筒、二三四條、六六條：聽一、四萬，一萬無役，四萬有斷么。 */
private val TANYAO_ONLY_ON_FOUR_WAIT: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 2),
    Tile.Numeric(Tile.Suit.Character, 3),
    Tile.Numeric(Tile.Suit.Dot, 4),
    Tile.Numeric(Tile.Suit.Dot, 5),
    Tile.Numeric(Tile.Suit.Dot, 6),
    Tile.Numeric(Tile.Suit.Dot, 7),
    Tile.Numeric(Tile.Suit.Dot, 7),
    Tile.Numeric(Tile.Suit.Dot, 7),
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Numeric(Tile.Suit.Bamboo, 3),
    Tile.Numeric(Tile.Suit.Bamboo, 4),
    Tile.Numeric(Tile.Suit.Bamboo, 6),
    Tile.Numeric(Tile.Suit.Bamboo, 6),
)

/** 二三萬、四五六筒、七八九筒、中中、二二條、北：尚未聽牌；碰中並打出北後聽一、四萬。 */
private val RED_DRAGON_PAIR_BEFORE_CALL: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 2),
    Tile.Numeric(Tile.Suit.Character, 3),
    Tile.Numeric(Tile.Suit.Dot, 4),
    Tile.Numeric(Tile.Suit.Dot, 5),
    Tile.Numeric(Tile.Suit.Dot, 6),
    Tile.Numeric(Tile.Suit.Dot, 7),
    Tile.Numeric(Tile.Suit.Dot, 8),
    Tile.Numeric(Tile.Suit.Dot, 9),
    Tile.Honor.Red,
    Tile.Honor.Red,
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Numeric(Tile.Suit.Bamboo, 2),
    Tile.Honor.North,
)

/** 二三萬、四五六條、七八九條、白白白、八八筒：聽一、四萬，兩張都有役牌白。 */
private val WHITE_DRAGON_TWO_SIDED_WAIT: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 2),
    Tile.Numeric(Tile.Suit.Character, 3),
    Tile.Numeric(Tile.Suit.Bamboo, 4),
    Tile.Numeric(Tile.Suit.Bamboo, 5),
    Tile.Numeric(Tile.Suit.Bamboo, 6),
    Tile.Numeric(Tile.Suit.Bamboo, 7),
    Tile.Numeric(Tile.Suit.Bamboo, 8),
    Tile.Numeric(Tile.Suit.Bamboo, 9),
    Tile.Honor.White,
    Tile.Honor.White,
    Tile.Honor.White,
    Tile.Numeric(Tile.Suit.Dot, 8),
    Tile.Numeric(Tile.Suit.Dot, 8),
)
