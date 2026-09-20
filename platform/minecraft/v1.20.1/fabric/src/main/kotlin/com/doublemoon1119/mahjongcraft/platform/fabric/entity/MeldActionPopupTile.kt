package com.doublemoon1119.mahjongcraft.platform.fabric.entity

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.DecisionTileOrientationDto
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import kotlin.uuid.Uuid

/**
 * 副露短暫提示中的一張牌。
 *
 * 提示比照決策卡片：全部牌面直立、依規則牌序排列，鳴取的那張以標記指出，不靠橫置或疊放表達。
 *
 * @property tileId 權威牌張 UUID。
 * @property claimed 是否為鳴取自他家的那張牌。
 */
data class MeldActionPopupTile(
    val tileId: Uuid,
    val claimed: Boolean,
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
