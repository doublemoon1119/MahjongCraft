package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.table.layout.PhysicalWallLayoutTransitionPhase
import com.doublemoon1119.mahjongcraft.platform.minecraft.dice.MahjongTableFacing
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TableLocation
import kotlin.uuid.Uuid

/**
 * 已由規則解析完成、等待在既有牌牆 entity 上播放的布局 transition。
 *
 * @property tableId 所屬麻將桌的穩定 UUID。
 * @property tableLocation 麻將桌 controller 的位置。
 * @property tableFacing 麻將桌 controller 的世界水平朝向。
 * @property dealerSeatIndex 本局莊家在固定座位列表中的 index。
 * @property stacksPerSide 牌牆每面的墩數。
 * @property sideCount 牌牆的面數，與玩家人數相同。
 * @property phases 規則提供的來源、目的與先後階段。
 * @property startGameTime 第一個階段可開始播放的絕對 server game time。
 */
data class MahjongTileWallTransitionPresentation(
    val tableId: Uuid,
    val tableLocation: TableLocation,
    val tableFacing: MahjongTableFacing,
    val dealerSeatIndex: Int,
    val stacksPerSide: Int,
    val sideCount: Int,
    val phases: List<PhysicalWallLayoutTransitionPhase>,
    val startGameTime: Long,
)
