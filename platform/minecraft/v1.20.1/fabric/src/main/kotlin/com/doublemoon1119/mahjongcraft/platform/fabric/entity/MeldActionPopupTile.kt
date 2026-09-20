package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.uuid.Uuid

/** 副露短暫提示中的一張牌，以及其在實際副露中的朝向與堆疊狀態。 */
data class MeldActionPopupTile(
    /** 權威牌張 UUID。 */
    val tileId: Uuid,
    /** 牌面在提示中的實際方向。 */
    val orientation: DecisionTileOrientationDto,
    /** 是否為加槓疊放在橫置牌上的第四張牌。 */
    val stacked: Boolean,
)

/**
 * 鳴取的那張牌在副露中的朝向：上家鳴取往左轉，對家與下家往右轉。
 *
 * 暗槓沒有鳴取來源（[RelativeDirection.Self]），四張都維持直立。
 */
fun RelativeDirection.claimedTileOrientation(): DecisionTileOrientationDto = when (this) {
    RelativeDirection.Left -> DecisionTileOrientationDto.ROTATED_LEFT
    RelativeDirection.Across, RelativeDirection.Right -> DecisionTileOrientationDto.ROTATED_RIGHT
    RelativeDirection.Self -> DecisionTileOrientationDto.UPRIGHT
}
