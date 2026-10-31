package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/** 判斷擺牌層的一格是否擋住牌、牌牆、骰子與桌面物件。 */
object TableSpaceObstruction {
    /**
     * 判斷一格是否擋住擺牌。
     *
     * 本桌自己的方塊可以進入擺牌層（例如桌緣），含水時也一樣；其他東西都算擋住，包括流進桌面的水、
     * 另一張桌子的方塊，以及火把、地毯這類沒有碰撞的方塊。
     *
     * @param P 方塊位置型別。
     * @param isAir 該格是否為空氣。
     * @param hasFluid 該格是否有流體。
     * @param owningController 該格方塊所屬桌子的控制方塊位置；不屬於任何桌子時為 null。
     * @param controller 正在檢查的桌子的控制方塊位置。
     * @return 該格擋住擺牌時為 true。
     */
    fun <P> isObstructing(
        isAir: Boolean,
        hasFluid: Boolean,
        owningController: P?,
        controller: P,
    ): Boolean = owningController != controller && (hasFluid || !isAir)
}
