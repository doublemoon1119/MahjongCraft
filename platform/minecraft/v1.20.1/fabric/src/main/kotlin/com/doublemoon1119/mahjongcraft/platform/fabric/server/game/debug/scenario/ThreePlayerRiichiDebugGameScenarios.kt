package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 三人日麻驗收用的 debug 情境，只能在三人日麻的桌子載入。
 *
 * 全部沿用 [SeatSetupScenario]：呼叫者是莊家、剛摸到牌，座位順序為呼叫者 → 下家 → 上家，兩家對手依排定的牌摸切。
 * 呼叫者預先有兩張捨牌，牌局不在第一巡。情境都停在還沒有人拔北的時候，拔北、補牌、搶北與槓都在載入後走正式流程。
 *
 * 牌牆開局的位置與三面牌牆的樣子另外用正式開局情境 `mahjongcraft:riichi_wall_opening` 驗收，它在三人日麻的桌子
 * 會依三人規則擲骰開門。
 */
object ThreePlayerRiichiDebugGameScenarios {
    /** 所有三人日麻驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 剛摸到北、手上還有一張北。先拔剛摸到的北，補到九萬；再拔手上的北，補到四條，可以用拔北的補牌自摸。
        SeatSetupScenario(
            id = "mahjongcraft:riichi_three_player_pull_north",
            invoker = SeatSpec(
                standingTiles = PINZU_STRAIGHT + listOf(TWO_BAMBOO, THREE_BAMBOO, NINE_CHARACTER, Tile.Honor.North),
                drawnTile = Tile.Honor.North,
                discards = listOf(Tile.Honor.South, Tile.Honor.West),
            ),
            liveWallFront = emptyList(),
            waitingTiles = BAMBOO_TWO_SIDED_WAITS,
            deadWallFront = listOf(NINE_CHARACTER, FOUR_BAMBOO),
            playerCount = THREE_PLAYER_COUNT,
        ),
        // 已立直、手上有一對北、剛摸到第三張北。只能拔剛摸到的北，補到四條可以自摸；手上那對北之後也不能拔。
        SeatSetupScenario(
            id = "mahjongcraft:riichi_three_player_pull_north_in_riichi",
            invoker = SeatSpec(
                standingTiles = PINZU_STRAIGHT + listOf(TWO_BAMBOO, THREE_BAMBOO, Tile.Honor.North, Tile.Honor.North),
                drawnTile = Tile.Honor.North,
                discards = listOf(Tile.Honor.South),
                riichiTile = Tile.Honor.West,
            ),
            liveWallFront = emptyList(),
            waitingTiles = BAMBOO_TWO_SIDED_WAITS,
            deadWallFront = listOf(FOUR_BAMBOO),
            playerCount = THREE_PLAYER_COUNT,
        ),
        // 呼叫者單騎聽北、有役牌中。打出剛摸到的九萬後，下家摸到北立刻拔北，呼叫者可以榮和這張北；
        // 選擇不和時，下家補牌後摸切。
        SeatSetupScenario(
            id = "mahjongcraft:riichi_three_player_rob_north",
            invoker = SeatSpec(
                standingTiles = PINZU_STRAIGHT + listOf(Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.North),
                drawnTile = NINE_CHARACTER,
                discards = listOf(Tile.Honor.South, Tile.Honor.West),
            ),
            downstream = SeatSpec(strategyKey = DebugScriptedAiStrategy.PULL_NORTH_FIRST_KEY),
            liveWallFront = listOf(Tile.Honor.North),
            waitingTiles = setOf(Tile.Honor.North),
            playerCount = THREE_PLAYER_COUNT,
        ),
        // 手上有一筒、九筒、一條各四張，剛摸到北。依序拔北（補九條）、槓一筒（補北）、拔北（補九條）、槓九筒（補九條）、
        // 槓九條、槓一條，共補牌六次、四槓，第 3、4 張槓寶牌指示牌會翻在從活牌尾端補進王牌的牌上。
        SeatSetupScenario(
            id = "mahjongcraft:riichi_three_player_kan_and_pull_north",
            invoker = SeatSpec(
                standingTiles = List(KAN_SIZE) { ONE_DOT } + List(KAN_SIZE) { NINE_DOT } + List(KAN_SIZE) { ONE_BAMBOO } + NINE_BAMBOO,
                drawnTile = Tile.Honor.North,
                discards = listOf(Tile.Honor.South, Tile.Honor.West),
            ),
            liveWallFront = emptyList(),
            waitingTiles = emptySet(),
            deadWallFront = listOf(NINE_BAMBOO, Tile.Honor.North, NINE_BAMBOO, NINE_BAMBOO),
            playerCount = THREE_PLAYER_COUNT,
        ),
    )
}

/** 一組槓的張數。 */
private const val KAN_SIZE: Int = 4

/** 一筒。 */
private val ONE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 1)

/** 九筒。 */
private val NINE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 9)

/** 一條。 */
private val ONE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 1)

/** 二條。 */
private val TWO_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 2)

/** 三條。 */
private val THREE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 3)

/** 四條。 */
private val FOUR_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 4)

/** 一二三四五六七八九筒：湊成一氣通貫，讓和牌不依賴其他役。 */
private val PINZU_STRAIGHT: List<Tile> = (1..9).map { value -> Tile.Numeric(Tile.Suit.Dot, value) }

/** 二三條聽的一條與四條。 */
private val BAMBOO_TWO_SIDED_WAITS: Set<Tile> = setOf(ONE_BAMBOO, FOUR_BAMBOO)
