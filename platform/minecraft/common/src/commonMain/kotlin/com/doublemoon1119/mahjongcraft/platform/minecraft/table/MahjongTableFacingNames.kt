package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.table.MahjongTableFacing

/**
 * 由存檔或同步資料中的穩定名稱解析桌面朝向；未知值使用北方作為安全預設。
 *
 * @param name 待解析的朝向名稱；null 或不認得的名稱會使用北方。
 * @return 解析出的桌面朝向。
 */
fun mahjongTableFacingFromNameOrDefault(name: String?): MahjongTableFacing = MahjongTableFacing.entries.firstOrNull { it.name == name } ?: MahjongTableFacing.NORTH
