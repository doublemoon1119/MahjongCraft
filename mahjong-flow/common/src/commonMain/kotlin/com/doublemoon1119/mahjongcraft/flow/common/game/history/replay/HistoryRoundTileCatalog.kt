package com.doublemoon1119.mahjongcraft.flow.common.game.history.replay

import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

/**
 * 局內已宣告的實體牌目錄，不包含所選交易之後才宣告的牌。
 *
 * @property tiles 依實體牌索引排列的牌面，帶當時保存的外觀與特徵；相同牌面可重複出現，各項代表不同實體。
 */
data class HistoryRoundTileCatalog(val tiles: List<TileFaceView>)
