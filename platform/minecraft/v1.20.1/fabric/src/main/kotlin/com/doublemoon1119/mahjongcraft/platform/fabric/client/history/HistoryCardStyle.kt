package com.doublemoon1119.mahjongcraft.platform.fabric.client.history

import net.minecraft.network.chat.Component

/** 牌譜卡片共用的尺寸與配色。 */
internal object HistoryCardStyle {
    /** 文字列高度。 */
    const val LINE_HEIGHT = 11

    /** 牌面寬度。 */
    const val TILE_WIDTH = 18

    /** 牌面高度。 */
    const val TILE_HEIGHT = 24

    /** 牌面起點間距。 */
    const val TILE_STEP = 20

    /** 頭像加上與後方文字的間距。 */
    const val PORTRAIT_STEP = 14

    /** 卡片內小按鈕的高度，也是放大鏡按鈕的邊長。 */
    const val MINI_BUTTON_SIZE = 16

    /** 和牌手牌群組相對卡片左側的縮排。 */
    const val HAND_INDENT = 46

    /** 依玩家列出牌時，每一列開頭的項目符號。 */
    val SEAT_LINE_BULLET: Component = Component.literal("• ")

    /** 一般文字。 */
    const val TEXT_COLOR = 0xffffff

    /** 次要文字。 */
    const val MUTED_COLOR = 0xaaaaaa

    /** 重要資訊。 */
    const val ACCENT_COLOR = 0x8ed5df

    /** 失敗訊息。 */
    const val ERROR_COLOR = 0xff6666
}
