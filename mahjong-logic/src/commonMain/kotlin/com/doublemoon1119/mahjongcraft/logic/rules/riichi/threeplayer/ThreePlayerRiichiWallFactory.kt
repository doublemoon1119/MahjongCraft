package com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer

import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.TileWallFactory
import kotlin.uuid.Uuid

/**
 * 三人日本麻將的 108 張牌山：萬子只有一萬與九萬，筒子、條子與字牌照常各 4 張。
 *
 * 赤寶牌為 2 張時，五筒與五條各有一張赤牌。
 *
 * @property config 三人日本麻將的規則配置。
 */
class ThreePlayerRiichiWallFactory(private val config: ThreePlayerRiichiRuleConfig) : TileWallFactory {
    override fun create(): TileWall {
        val tiles = mutableListOf<IdentifiedTile>()
        val hasRedFives = config.redDoraCount == ThreePlayerRiichiRuleConfig.THREE_PLAYER_RED_DORA_COUNT

        Tile.Suit.entries.forEach { suit ->
            val values = if (suit == Tile.Suit.Character) listOf(1, 9) else (1..9).toList()
            values.forEach { value ->
                repeat(COPIES_PER_TILE) { count ->
                    val isRedFive = hasRedFives && suit != Tile.Suit.Character && value == 5 && count == 0
                    val tile = if (isRedFive) RiichiTileTypes.redFive(suit) else Tile.Numeric(suit, value)
                    tiles += IdentifiedTile(Uuid.random(), tile)
                }
            }
        }
        HONORS.forEach { honor ->
            repeat(COPIES_PER_TILE) { tiles += IdentifiedTile(Uuid.random(), honor) }
        }
        tiles.shuffle()
        return TileWall(tiles)
    }

    private companion object {
        /** 每種牌的張數。 */
        const val COPIES_PER_TILE = 4

        /** 風牌與三元牌。 */
        val HONORS = listOf(
            Tile.Honor.East,
            Tile.Honor.South,
            Tile.Honor.West,
            Tile.Honor.North,
            Tile.Honor.White,
            Tile.Honor.Green,
            Tile.Honor.Red,
        )
    }
}
