package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.scenario

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.platform.minecraft.server.game.debug.scenario.DebugGameScenario

/**
 * 宣傳截圖用的四人日麻 debug 情境：打到中盤、看起來像真的在打牌的桌面。
 *
 * - 呼叫者是莊家，從上家碰了發，手上是一般的進張中的牌，剛摸到下一張牌。
 * - 莊家與下家各打了八張，對家打了七張後立直，上家打了八張、其中一張被莊家碰走，牌河都混合字牌、么九與中張。
 * - 兩個情境的牌河與副露相同，只差呼叫者剛摸到的牌。
 */
object RiichiShowcaseDebugGameScenarios {
    /** 所有宣傳截圖情境。 */
    val all: List<DebugGameScenario> = listOf(
        // 剛摸到西風，還沒有聽牌，拍牌桌全景使用。
        showcaseScenario(id = "mahjongcraft:riichi/debug/showcase_midgame", drawnTile = Tile.Honor.West, waitingTiles = emptySet()),
        // 剛摸到二條，自摸二、五條，有役牌發，拍和牌演出使用。
        showcaseScenario(id = "mahjongcraft:riichi/debug/showcase_tsumo", drawnTile = TWO_BAMBOO, waitingTiles = setOf(TWO_BAMBOO, FIVE_BAMBOO)),
    )

    /**
     * 建立宣傳截圖的牌桌。
     *
     * @param id 完整的 namespaced 情境 ID。
     * @param drawnTile 呼叫者剛摸到的牌。
     * @param waitingTiles 呼叫者的和牌張，不放進王牌區。
     * @return 情境。
     */
    private fun showcaseScenario(id: String, drawnTile: Tile, waitingTiles: Set<Tile>): DebugGameScenario = SeatSetupScenario(
        id = id,
        invoker = SeatSpec(
            standingTiles = SHOWCASE_HAND,
            pons = listOf(ScenarioPon(tile = Tile.Honor.Green, from = RelativeDirection.Left)),
            drawnTile = drawnTile,
            discards = listOf(Tile.Honor.North, ONE_BAMBOO, NINE_CHARACTER, Tile.Honor.South, ONE_DOT, EIGHT_CHARACTER, Tile.Honor.White, SEVEN_BAMBOO),
        ),
        downstream = SeatSpec(
            discards = listOf(ONE_CHARACTER, Tile.Honor.North, NINE_BAMBOO, Tile.Honor.East, TWO_DOT, EIGHT_CHARACTER, Tile.Honor.Red, SIX_BAMBOO),
        ),
        across = SeatSpec(
            discards = listOf(Tile.Honor.South, ONE_CHARACTER, Tile.Honor.West, NINE_BAMBOO, FOUR_DOT, SEVEN_BAMBOO, TWO_BAMBOO),
            riichiTile = THREE_DOT,
        ),
        upstream = SeatSpec(
            discards = listOf(Tile.Honor.North, NINE_CHARACTER, ONE_DOT, Tile.Honor.East, THREE_CHARACTER, SIX_DOT, Tile.Honor.Red),
        ),
        liveWallFront = emptyList(),
        waitingTiles = waitingTiles,
    )
}

/** 宣傳情境呼叫者的立牌：二三四萬、五六七筒、三四條、九筒對子，等二、五條。 */
private val SHOWCASE_HAND: List<Tile> = listOf(
    Tile.Numeric(Tile.Suit.Character, 2),
    Tile.Numeric(Tile.Suit.Character, 3),
    Tile.Numeric(Tile.Suit.Character, 4),
    Tile.Numeric(Tile.Suit.Dot, 5),
    Tile.Numeric(Tile.Suit.Dot, 6),
    Tile.Numeric(Tile.Suit.Dot, 7),
    Tile.Numeric(Tile.Suit.Bamboo, 3),
    Tile.Numeric(Tile.Suit.Bamboo, 4),
    Tile.Numeric(Tile.Suit.Dot, 9),
    Tile.Numeric(Tile.Suit.Dot, 9),
)

/** 三萬。 */
private val THREE_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 3)

/** 八萬。 */
private val EIGHT_CHARACTER: Tile = Tile.Numeric(Tile.Suit.Character, 8)

/** 一筒。 */
private val ONE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 1)

/** 二筒。 */
private val TWO_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 2)

/** 三筒。 */
private val THREE_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 3)

/** 四筒。 */
private val FOUR_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 4)

/** 六筒。 */
private val SIX_DOT: Tile = Tile.Numeric(Tile.Suit.Dot, 6)

/** 一條。 */
private val ONE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 1)

/** 二條。 */
private val TWO_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 2)

/** 五條。 */
private val FIVE_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 5)

/** 六條。 */
private val SIX_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 6)

/** 七條。 */
private val SEVEN_BAMBOO: Tile = Tile.Numeric(Tile.Suit.Bamboo, 7)
