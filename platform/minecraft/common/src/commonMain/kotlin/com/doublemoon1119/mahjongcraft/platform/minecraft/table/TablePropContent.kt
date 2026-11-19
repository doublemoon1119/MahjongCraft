package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView

/**
 * 桌面物件要呈現的內容。
 */
sealed interface TablePropContent {
    /**
     * 由種類自行解讀的變體內容，例如點棒面額或第三方籌碼樣式。
     *
     * @property value 種類內的內容識別碼。
     */
    data class CustomVariant(val value: String) : TablePropContent {
        init {
            require(value.isNotBlank()) { "Table prop variant must not be blank" }
        }
    }

    /**
     * 只展示牌面，不代表牌山或手牌中的實體牌。
     *
     * 牌面帶有組件的公開效果（例如透明或赤五），外觀與同樣牌面的正式牌張一致。展示桌上某張牌的副本時，用
     * [TablePropTileFaces] 取得它的公開牌面；展示不屬於桌況的牌面時直接建立 [TileFaceView]。
     *
     * @property face 要展示的公開牌面。
     */
    data class FaceUpTile(val face: TileFaceView) : TablePropContent
}
