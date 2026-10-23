package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/**
 * 開發用可宣告不聽四人日麻的驗收情境。
 *
 * 沿用 [SeatSetupScenario]：呼叫者是莊家、剛摸到牌，座位順序為呼叫者 → 下家 → 對家 → 上家。載入後改用開發用的
 * 可宣告不聽四人日麻；其他四人情境載入後恢復一般四人日麻。
 */
internal object RiichiRuleDecisionDebugGameScenarios {
    /** 所有可宣告不聽的驗收情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 牌山快摸完時流局：活牌只剩三張東，其餘的牌已在各家牌河。呼叫者打出剛摸到的西後，三家各摸切一張東，
        // 輪到呼叫者摸牌時牌山已空。呼叫者聽一、四條，下家聽四、七條，兩人都沒有立直，結算前要選擇公開聽牌或宣告
        // 不聽；對家已立直、聽白與發，不能宣告不聽；上家不聽。流局結算後所有人再確認一次才進入下一局。
        SeatSetupScenario(
            id = "mahjongcraft:noten_declaration_exhaustive_draw",
            invoker = SeatSpec(
                standingTiles = PINZU_STRAIGHT + listOf(bamboo(2), bamboo(3), character(9), character(9)),
                drawnTile = Tile.Honor.West,
                discards = listOf(Tile.Honor.South),
            ),
            downstream = SeatSpec(
                standingTiles = MANZU_STRAIGHT + listOf(bamboo(5), bamboo(6), Tile.Honor.Red, Tile.Honor.Red),
            ),
            across = SeatSpec(
                standingTiles = listOf(
                    character(2),
                    character(3),
                    character(4),
                    dot(6),
                    dot(7),
                    dot(8),
                    bamboo(6),
                    bamboo(7),
                    bamboo(8),
                    Tile.Honor.White,
                    Tile.Honor.White,
                    Tile.Honor.Green,
                    Tile.Honor.Green,
                ),
                riichiTile = Tile.Honor.North,
            ),
            upstream = SeatSpec(
                standingTiles = listOf(
                    character(1),
                    character(4),
                    character(7),
                    dot(2),
                    dot(5),
                    dot(8),
                    bamboo(3),
                    bamboo(6),
                    bamboo(9),
                    Tile.Honor.South,
                    Tile.Honor.West,
                    Tile.Honor.North,
                    Tile.Honor.White,
                ),
            ),
            liveWallFront = listOf(Tile.Honor.East, Tile.Honor.East, Tile.Honor.East),
            waitingTiles = setOf(bamboo(1), bamboo(4)),
            liveWallIsFrontOnly = true,
            rule = DebugScenarioRule.NOTEN_DECLARATION,
        ),
    )
}

/** 一二三四五六七八九筒：呼叫者聽牌形的主體。 */
private val PINZU_STRAIGHT: List<Tile> = (1..9).map(::dot)

/** 一二三四五六七八九萬：下家聽牌形的主體。 */
private val MANZU_STRAIGHT: List<Tile> = (1..9).map(::character)

/**
 * 建立萬子。
 *
 * @param value 牌面數字。
 * @return 該數字的萬子。
 */
private fun character(value: Int): Tile = Tile.Numeric(Tile.Suit.Character, value)

/**
 * 建立筒子。
 *
 * @param value 牌面數字。
 * @return 該數字的筒子。
 */
private fun dot(value: Int): Tile = Tile.Numeric(Tile.Suit.Dot, value)

/**
 * 建立索子。
 *
 * @param value 牌面數字。
 * @return 該數字的索子。
 */
private fun bamboo(value: Int): Tile = Tile.Numeric(Tile.Suit.Bamboo, value)
