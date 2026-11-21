package com.doublemoon1119.mahjongcraft.platform.fabric.client.gui

/** 各畫面卡片共用的底色，例如房間成員卡片與決策畫面的選項卡片。 */
internal object CardBackground {
    /** 卡片底色的 ARGB 顏色。 */
    const val COLOR: Int = 0xA0202838.toInt()

    /** 可點擊卡片在游標停留時的 ARGB 底色。 */
    const val HOVER_COLOR: Int = 0xC0384660.toInt()

    /** 疊在牌桌場景旁、需要和場景色調區隔的卡片底色，例如牌譜回放的交易列表。 */
    const val NEUTRAL_COLOR: Int = 0x8C333333.toInt()

    /** [NEUTRAL_COLOR] 卡片在游標停留時的底色。 */
    const val NEUTRAL_HOVER_COLOR: Int = 0xB0444444.toInt()
}
