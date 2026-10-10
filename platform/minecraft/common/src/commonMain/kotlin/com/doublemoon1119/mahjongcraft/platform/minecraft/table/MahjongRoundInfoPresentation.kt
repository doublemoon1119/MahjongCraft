package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import kotlin.uuid.Uuid

/**
 * 已由伺服器決定的桌面中央局況顯示資料。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property lines 要顯示的完整內容，由 Minecraft 規則呈現 provider 依當下狀態建立，依序顯示——這個
 * entity 是「找到既有的就地更新」模式，呼叫端每次都要重新算好完整內容一起
 * 傳入，不能只在部分呼叫點帶上，否則沒帶的呼叫會把之前顯示的內容覆蓋回空清單。
 */
data class MahjongRoundInfoPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val lines: List<RoundInfoLine>,
)
