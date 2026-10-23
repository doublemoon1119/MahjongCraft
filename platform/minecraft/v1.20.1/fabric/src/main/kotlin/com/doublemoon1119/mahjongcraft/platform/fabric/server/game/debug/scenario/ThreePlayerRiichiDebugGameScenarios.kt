package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 三人日麻驗收用的 debug 情境，只能在三人日麻的桌子載入。
 *
 * 全部沿用 [SeatSetupScenario]：呼叫者是莊家、剛摸到牌，座位順序為呼叫者 → 下家 → 上家，兩家對手依排定的牌摸切。
 * 呼叫者預先有捨牌，牌局不在第一巡。情境都停在還沒有人拔北的時候，拔北、補牌、搶北與槓都在載入後走正式流程。
 *
 * `auto_north_riichi` 開頭的情境改用開發用的自動拔北三人日麻：任何人摸到北都會自動拔北並補牌，拔北的人付
 * 1000 點進供託；暗槓與加槓由另外兩家各付 500 點給開槓者，明槓由放槓者付 1000 點。其餘情境載入後恢復一般的三人日麻。
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
        // 白從上家碰、發從下家碰，手上兩張北、剛摸到第三張北，手牌一開始就快碰到副露。依序拔三次北，補到七筒、八筒、
        // 四筒，每拔一次桌角就多一張北、手牌要再讓開一些；第三次補到的四筒可以自摸。活牌只剩四張，其餘的牌已在三家
        // 牌河；每次拔北都從活牌尾端補一張進王牌，不自摸而打出後，下家摸走最後一張活牌打出就流局，呼叫者聽一、四筒。
        SeatSetupScenario(
            id = "mahjongcraft:riichi_three_player_pull_north_with_melds",
            invoker = SeatSpec(
                standingTiles = listOf(
                    Tile.Honor.North,
                    Tile.Honor.North,
                    NINE_CHARACTER,
                    NINE_CHARACTER,
                    TWO_DOT,
                    THREE_DOT,
                    SIX_DOT,
                ),
                pons = listOf(
                    ScenarioPon(tile = Tile.Honor.White, from = RelativeDirection.Left),
                    ScenarioPon(tile = Tile.Honor.Green, from = RelativeDirection.Right),
                ),
                drawnTile = Tile.Honor.North,
            ),
            liveWallFront = listOf(Tile.Honor.West, Tile.Honor.East, Tile.Honor.West, Tile.Honor.East),
            waitingTiles = DOT_TWO_SIDED_WAITS,
            deadWallFront = listOf(SEVEN_DOT, EIGHT_DOT, FOUR_DOT),
            playerCount = THREE_PLAYER_COUNT,
            liveWallIsFrontOnly = true,
        ),
        // 自動拔北：打出剛摸到的西後，兩家對手各摸切一張東，呼叫者摸到北。北自動拔出並補到北、再拔再補到北、
        // 再拔再補到四條，連續三步，每一步都付 1000 點進供託；最後的四條可以嶺上開花自摸。
        SeatSetupScenario(
            id = "mahjongcraft:auto_north_riichi_chain",
            invoker = SeatSpec(
                standingTiles = PINZU_STRAIGHT + listOf(TWO_BAMBOO, THREE_BAMBOO, NINE_CHARACTER, NINE_CHARACTER),
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South),
            ),
            liveWallFront = listOf(Tile.Honor.East, Tile.Honor.East, Tile.Honor.North),
            waitingTiles = BAMBOO_TWO_SIDED_WAITS,
            deadWallFront = listOf(Tile.Honor.North, Tile.Honor.North, FOUR_BAMBOO),
            playerCount = THREE_PLAYER_COUNT,
            rule = DebugScenarioRule.AUTO_NORTH,
        ),
        // 自動拔北被搶：呼叫者碰出中與白、單騎聽北。打出剛摸到的九萬後，下家摸到北自動拔出，呼叫者可以榮和這張北；
        // 被搶的拔北不收付。選擇不和時，下家付 1000 點進供託、補牌後摸切；供託變多讓莊家角落變寬，呼叫者的兩組副露
        // 一起平移讓出位置。
        SeatSetupScenario(
            id = "mahjongcraft:auto_north_riichi_rob",
            invoker = SeatSpec(
                standingTiles = PINZU_TWO_SEQUENCES + Tile.Honor.North,
                pons = listOf(
                    ScenarioPon(tile = Tile.Honor.Red, from = RelativeDirection.Left),
                    ScenarioPon(tile = Tile.Honor.White, from = RelativeDirection.Right),
                ),
                drawnTile = NINE_CHARACTER,
                discards = listOf(Tile.Honor.South, Tile.Honor.West),
            ),
            liveWallFront = listOf(Tile.Honor.North),
            waitingTiles = setOf(Tile.Honor.North),
            playerCount = THREE_PLAYER_COUNT,
            rule = DebugScenarioRule.AUTO_NORTH,
        ),
        // 沒有牌可補：活牌只剩三張，其餘的牌已在三家牌河。打出剛摸到的西後，兩家對手各摸切一張東，呼叫者摸到最後
        // 一張活牌是北；活牌已經摸完，不能拔北補牌，本局直接流局。呼叫者聽一、四條，流局時聽牌。一般三人日麻摸到最後一張
        // 仍要打一張才流局，這裡立即流局是測試規則為了驗證「沒牌可補就結束本局」的設計。
        SeatSetupScenario(
            id = "mahjongcraft:auto_north_riichi_last_tile",
            invoker = SeatSpec(
                standingTiles = PINZU_STRAIGHT + listOf(TWO_BAMBOO, THREE_BAMBOO, NINE_CHARACTER, NINE_CHARACTER),
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South),
            ),
            liveWallFront = listOf(Tile.Honor.East, Tile.Honor.East, Tile.Honor.North),
            waitingTiles = BAMBOO_TWO_SIDED_WAITS,
            playerCount = THREE_PLAYER_COUNT,
            liveWallIsFrontOnly = true,
            rule = DebugScenarioRule.AUTO_NORTH,
        ),
        // 槓牌收付：手上有四張一筒、三張一條，九筒已從上家碰出。先暗槓一筒（另外兩家各付 500 點），嶺上補到九筒後
        // 加槓（再各付 500 點），嶺上補到南後打牌；下家摸切一條時可以明槓（下家付 1000 點），嶺上補到東。
        SeatSetupScenario(
            id = "mahjongcraft:auto_north_riichi_kan_payments",
            invoker = SeatSpec(
                standingTiles = List(KAN_SIZE) { ONE_DOT } + List(KAN_SIZE - 1) { ONE_BAMBOO } + listOf(NINE_CHARACTER, NINE_CHARACTER, Tile.Honor.Red),
                pons = listOf(ScenarioPon(tile = NINE_DOT, from = RelativeDirection.Left)),
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South),
            ),
            liveWallFront = listOf(ONE_BAMBOO),
            waitingTiles = emptySet(),
            deadWallFront = listOf(NINE_DOT, Tile.Honor.South, Tile.Honor.East),
            playerCount = THREE_PLAYER_COUNT,
            rule = DebugScenarioRule.AUTO_NORTH,
        ),
    )
}

/** 一組槓的張數。 */
private const val KAN_SIZE: Int = 4

/** 一筒。 */
private val ONE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 1)

/** 九筒。 */
private val NINE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 9)

/** 二筒。 */
private val TWO_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 2)

/** 三筒。 */
private val THREE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 3)

/** 四筒。 */
private val FOUR_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 4)

/** 六筒。 */
private val SIX_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 6)

/** 七筒。 */
private val SEVEN_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 7)

/** 八筒。 */
private val EIGHT_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 8)

/** 二三筒聽的一筒與四筒。 */
private val DOT_TWO_SIDED_WAITS: Set<Tile> = setOf(ONE_DOT, FOUR_DOT)

/** 一條。 */
private val ONE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 1)

/** 二條。 */
private val TWO_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 2)

/** 三條。 */
private val THREE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 3)

/** 四條。 */
private val FOUR_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 4)

/** 一二三筒與四五六筒兩組順子。 */
private val PINZU_TWO_SEQUENCES: List<Tile> = (1..6).map { value -> Tile.Numeric(Tile.Suit.Dot, value) }

/** 一二三四五六七八九筒：湊成一氣通貫，讓和牌不依賴其他役。 */
private val PINZU_STRAIGHT: List<Tile> = (1..9).map { value -> Tile.Numeric(Tile.Suit.Dot, value) }

/** 二三條聽的一條與四條。 */
private val BAMBOO_TWO_SIDED_WAITS: Set<Tile> = setOf(ONE_BAMBOO, FOUR_BAMBOO)
