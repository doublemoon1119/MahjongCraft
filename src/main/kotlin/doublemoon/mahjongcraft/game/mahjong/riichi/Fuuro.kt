package doublemoon.mahjongcraft.game.mahjong.riichi

import doublemoon.mahjongcraft.entity.MahjongTileEntity
import org.mahjong4j.hands.Mentsu

/**
 * 副露 (英文好像叫 call)
 * */
class Fuuro(
    val mentsu: Mentsu,
    val tileMjEntities: MutableList<MahjongTileEntity>,
    val claimTarget: ClaimTarget,
    val claimTile: MahjongTileEntity
)