package com.doublemoon1119.mahjongcraft.platform.minecraft.api.table

/** 麻將桌控制點的世界水平朝向；以共用型別表示，避免依賴特定 loader 或 Minecraft 版本的方向型別。 */
enum class MahjongTableFacing {
    /** 朝向世界北方。 */
    NORTH,

    /** 朝向世界東方。 */
    EAST,

    /** 朝向世界南方。 */
    SOUTH,

    /** 朝向世界西方。 */
    WEST,
}
