package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenario

/**
 * 副露擺放驗收用的四人日麻 debug 情境。
 *
 * 全部沿用 [SeatSetupScenario]：呼叫者是莊家、剛摸到牌，其他三家依排定的牌摸切。
 */
object RiichiMeldDebugGameScenarios {
    /** 所有副露擺放驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 白從上家碰、東從對家碰、發從下家碰，剛摸到第四張白。加槓白補到八筒，暗槓八筒補到九萬，嶺上自摸；
        // 結算與牌譜可以一次看到三個方向的碰、加槓與暗槓的擺法。
        SeatSetupScenario(
            id = "mahjongcraft:riichi/debug/meld_showcase",
            invoker = SeatSpec(
                standingTiles = List(MELD_TILE_COUNT) { EIGHT_DOT } + NINE_CHARACTER,
                pons = listOf(
                    ScenarioPon(tile = Tile.Honor.White, from = RelativeDirection.Left),
                    ScenarioPon(tile = Tile.Honor.East, from = RelativeDirection.Across),
                    ScenarioPon(tile = Tile.Honor.Green, from = RelativeDirection.Right),
                ),
                drawnTile = Tile.Honor.White,
                discards = listOf(Tile.Honor.South, Tile.Honor.North),
            ),
            liveWallFront = emptyList(),
            waitingTiles = setOf(NINE_CHARACTER),
            deadWallFront = listOf(EIGHT_DOT, NINE_CHARACTER),
        ),
        // 只能在鷲巢麻將的桌子載入。白從上家碰、手上有三張五筒與兩張三條、牌河有兩張南；同種牌先取不透明的那張，
        // 所以碰出的白、手上的五筒與三條、牌河的南都同時有透明與不透明的牌，可以確認各區域的透明牌外觀與
        // 牌面公開。之後對手打出五筒時，碰會出現「兩張透明」與「透明加不透明」兩個選項。
        SeatSetupScenario(
            id = "mahjongcraft:riichi/debug/washizu_tiles",
            invoker = SeatSpec(
                standingTiles = List(MELD_TILE_COUNT) { FIVE_DOT } + listOf(THREE_BAMBOO, THREE_BAMBOO, SIX_CHARACTER, SEVEN_CHARACTER, EIGHT_CHARACTER) +
                    listOf(NINE_DOT, ONE_BAMBOO),
                pons = listOf(ScenarioPon(tile = Tile.Honor.White, from = RelativeDirection.Left)),
                drawnTile = Tile.Honor.East,
                discards = listOf(Tile.Honor.South, Tile.Honor.South),
            ),
            liveWallFront = emptyList(),
            waitingTiles = emptySet(),
            requiresTransparentTiles = true,
        ),
    )
}

/** 五筒。 */
private val FIVE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 5)

/** 九筒。 */
private val NINE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 9)

/** 一條。 */
private val ONE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 1)

/** 三條。 */
private val THREE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 3)

/** 六萬。 */
private val SIX_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 6)

/** 七萬。 */
private val SEVEN_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 7)

/** 八萬。 */
private val EIGHT_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 8)

/** 八筒。 */
private val EIGHT_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 8)
