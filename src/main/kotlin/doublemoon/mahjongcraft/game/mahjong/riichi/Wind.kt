package doublemoon.mahjongcraft.game.mahjong.riichi

import org.mahjong4j.tile.Tile
import doublemoon.mahjongcraft.MOD_ID

enum class Wind(
    val tile: Tile
) {
    EAST(Tile.TON),
    SOUTH(Tile.NAN),
    WEST(Tile.SHA),
    NORTH(Tile.PEI);

    val lang: String = "$MOD_ID.game.wind.${name.lowercase()}"
}