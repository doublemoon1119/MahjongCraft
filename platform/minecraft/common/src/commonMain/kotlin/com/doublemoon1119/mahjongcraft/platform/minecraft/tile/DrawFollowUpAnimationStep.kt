package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import kotlin.uuid.Uuid

/**
 * 摸牌後自動處理的一步在桌面上的動畫：摸牌位上的牌移到移出區，再從牌牆補一張牌到摸牌位。
 *
 * @property setAsideTileId 這一步從摸牌位移出的牌。
 * @property drawnTileId 這一步補到摸牌位的牌；補牌因搶和視窗暫停時為 null。
 */
data class DrawFollowUpAnimationStep(
    val setAsideTileId: Uuid,
    val drawnTileId: Uuid?,
)
