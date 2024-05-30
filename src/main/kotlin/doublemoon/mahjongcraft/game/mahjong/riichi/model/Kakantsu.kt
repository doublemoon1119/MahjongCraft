package doublemoon.mahjongcraft.game.mahjong.riichi.model

import org.mahjong4j.hands.Kantsu
import org.mahjong4j.tile.Tile


/**
 * 加槓子, 加槓專用
 * */
class Kakantsu(
    identifierTile: Tile
) : Kantsu(true, identifierTile)