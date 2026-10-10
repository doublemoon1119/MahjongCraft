package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

/**
 * 歷史實體牌參照，只能搭配同一對局、同一局的牌目錄使用。
 *
 * @property tileIndex 局內實體牌的零起算索引，不是牌種字典索引或原始 UUID。
 */
@JvmInline
value class HistoryTileReference(val tileIndex: Int)
