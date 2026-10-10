package com.doublemoon1119.mahjongcraft.platform.minecraft.dice

/** 麻將桌 controller 的世界水平朝向；避免共用 layout 依賴特定 Minecraft 版本的 Direction。 */
enum class MahjongTableFacing {
    /** 朝向世界北方。 */
    NORTH,

    /** 朝向世界東方。 */
    EAST,

    /** 朝向世界南方。 */
    SOUTH,

    /** 朝向世界西方。 */
    WEST,
    ;

    companion object {
        /** 由穩定名稱解析桌面朝向；未知值使用北方作為安全預設。 */
        fun fromNameOrDefault(name: String?): MahjongTableFacing = entries.firstOrNull { it.name == name } ?: NORTH
    }
}
