package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.base.Tile

/** 歷史實體牌參照，只能搭配同一對局、同一局的牌目錄使用。
 * @property tileIndex 局內實體牌的零起算索引，不是牌種字典索引或原始 UUID。
 */
@JvmInline
value class HistoryTileReference(val tileIndex: Int)

/** 局內已宣告的實體牌目錄，不包含所選交易之後才宣告的牌。
 * @property tiles 依實體牌索引排列的牌種；相同牌種可重複出現，各項代表不同實體。
 */
data class HistoryRoundTileCatalog(val tiles: List<Tile>)
