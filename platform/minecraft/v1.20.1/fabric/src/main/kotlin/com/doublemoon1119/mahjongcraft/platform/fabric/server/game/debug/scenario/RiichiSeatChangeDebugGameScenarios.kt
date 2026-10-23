package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 開發用和牌者坐莊、每局換位四人日麻的驗收情境。
 *
 * 沿用 [SeatSetupScenario]：呼叫者是莊家、剛摸到牌，座位順序為呼叫者 → 下家 → 對家 → 上家。載入後改用開發用的
 * 和牌者坐莊、每局換位四人日麻；其他四人情境載入後恢復一般四人日麻。
 */
internal object RiichiSeatChangeDebugGameScenarios {
    /** 所有和牌者坐莊、每局換位的驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 子家榮和後由和牌者坐莊並換位：呼叫者打出剛摸到的西，下家單騎聽西、有一氣通貫與役牌中，會榮和這張西。
        // 下一局由下家坐莊，所有玩家往下一個座位輪換一格。
        SeatSetupScenario(
            id = "mahjongcraft:winner_dealer_seat_rotation",
            invoker = SeatSpec(
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South),
            ),
            downstream = SeatSpec(
                standingTiles = PINZU_STRAIGHT + listOf(Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.Red, Tile.Honor.West),
                discards = listOf(Tile.Honor.North),
                strategyKey = DebugScriptedAiStrategy.WIN_FIRST_KEY,
            ),
            liveWallFront = emptyList(),
            waitingTiles = setOf(Tile.Honor.West),
            rule = DebugScenarioRule.WINNER_DEALER,
        ),
    )
}

/** 一二三四五六七八九筒：湊成一氣通貫，讓和牌不依賴其他役。 */
private val PINZU_STRAIGHT: List<Tile> = (1..9).map { value -> Tile.Numeric(Tile.Suit.Dot, value) }
