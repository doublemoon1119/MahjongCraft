package com.doublemoon1119.mahjongcraft.platform.minecraft.api.table

/**
 * 麻將桌的控制點，由控制方塊的方塊實體實作。
 *
 * 控制方塊所在位置就是桌子布局的原點，桌面固定在控制方塊上方一格；MahjongCraft 依這個位置與 [mahjongTableFacing]
 * 擺放牌、牌牆、骰子、桌面物件與面板。桌子由哪些方塊組成、外觀與碰撞由實作者決定。
 */
interface MahjongTableController {
    /** MahjongCraft 提供的桌子狀態；實作者建立一份，並在方塊實體存讀檔時交由所用 loader 的 MahjongCraft 函式處理。 */
    val mahjongTableState: MahjongTableControllerState

    /** 桌子朝向。 */
    val mahjongTableFacing: MahjongTableFacing
}
