package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.loader

/**
 * 光影模組是否正在畫陰影圖：開了光影包時，光影模組每一幀會先從光源方向把世界畫一次，得出哪裡被擋住。
 *
 * 光影模組因載入器而異，由載入器提供。沒有安裝光影模組時一律為否。
 */
fun interface ShadowPassProbe {
    /**
     * 目前是否在畫陰影圖。
     *
     * @return 正在畫陰影圖時為 true。
     */
    fun active(): Boolean
}
