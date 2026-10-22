package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile

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
            id = "mahjongcraft:riichi_meld_showcase",
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
    )
}

/** 八筒。 */
private val EIGHT_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 8)
