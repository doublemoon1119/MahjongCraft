package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import kotlin.uuid.Uuid

/**
 * 查詢桌況中某張牌對所有人公開的牌面，供桌上物件展示牌張副本。
 *
 * 牌面帶有組件的公開效果，與同一張牌在桌上的正式外觀一致。還沒公開的牌查不到牌面，副本不會洩漏未公開的內容。
 */
fun interface TablePropTileFaces {
    /**
     * 取得一張牌的公開牌面。
     *
     * @param tileId 桌況中的牌張識別碼。
     * @return 所有人都看得到的牌面；牌不在桌況中或還沒公開時為 null。
     */
    fun publicFace(tileId: Uuid): TileFaceView?
}
