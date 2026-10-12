package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 版面計算取得文字寬度的邊界。
 *
 * 文字寬度只有實際的字型量測得出來，因此由平台實作；解算本身不認識任何字型或繪製型別。
 */
interface PresentationTextMeasurer {
    /**
     * 量測本地化文字的寬度。
     *
     * @param text 含 translation key 與參數的文字值。
     * @return 實際字型寬度。
     */
    fun measure(text: PresentationValue.TextValue): Float

    /**
     * 量測已經是最終字串（例如玩家顯示名稱）的寬度。
     *
     * @param text 要量測的純文字。
     * @return 實際字型寬度。
     */
    fun measurePlain(text: String): Float
}
