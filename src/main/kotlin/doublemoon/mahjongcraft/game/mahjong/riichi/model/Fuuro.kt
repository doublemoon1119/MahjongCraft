package doublemoon.mahjongcraft.game.mahjong.riichi.model

import doublemoon.mahjongcraft.entity.MahjongTileEntity
import org.mahjong4j.hands.Mentsu

/**
 * 副露 (英文好像叫 call)
 * */
class Fuuro(
    val mentsu: Mentsu,
    val tileMjEntities: List<MahjongTileEntity>,
    val claimTarget: ClaimTarget,
    val claimTile: MahjongTileEntity
)