package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 一發驗收用的日麻 debug 情境。
 *
 * 全部沿用 [SeatSetupScenario] 的座位設定與排定摸打。呼叫者聽一、四萬，剛摸到西風，預先有兩張捨牌，因此宣告立直
 * 不是雙立直；載入後由呼叫者宣告立直並打出西風，其他三家依排定的牌摸切，期間沒有任何鳴牌。
 */
object RiichiIppatsuDebugGameScenarios {
    /** 所有一發驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 三家依序摸切北、白、九萬，呼叫者立直後的下一次摸牌摸到四萬，可以自摸一發。
        SeatSetupScenario(
            id = "mahjongcraft:riichi_ippatsu_tsumo",
            invoker = IPPATSU_INVOKER,
            liveWallFront = listOf(Tile.Honor.North, Tile.Honor.White, NINE_CHARACTER, FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
        // 下家摸到四萬後立刻打出，呼叫者可以榮和一發。
        SeatSetupScenario(
            id = "mahjongcraft:riichi_ippatsu_ron",
            invoker = IPPATSU_INVOKER,
            liveWallFront = listOf(FOUR_CHARACTER),
            waitingTiles = WAITING_TILES,
        ),
    )
}

/** 一發情境的呼叫者：聽一、四萬，剛摸到西風，預先打過南風與九條。 */
private val IPPATSU_INVOKER: SeatSpec = SeatSpec(
    standingTiles = YAKUHAI_TWO_SIDED_WAIT,
    drawnTile = Tile.Honor.West,
    discards = listOf(Tile.Honor.South, NINE_BAMBOO),
)
