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
    ;

    companion object {
        /**
         * 由穩定名稱解析桌面朝向；未知值使用北方作為安全預設。
         *
         * @param name 待解析的朝向名稱；null 或不認得的名稱會使用北方。
         * @return 解析出的桌面朝向。
         */
        fun fromNameOrDefault(name: String?): MahjongTableFacing = entries.firstOrNull { it.name == name } ?: NORTH
    }
}
