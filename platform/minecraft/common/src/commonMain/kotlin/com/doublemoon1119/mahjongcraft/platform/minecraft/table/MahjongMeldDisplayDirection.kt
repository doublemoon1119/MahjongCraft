package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection

/**
 * 將副露來源方向轉成牌面呈現時使用的方向。
 *
 * 這個轉換只影響畫面排列，不改變規則與對局狀態中的方向意義。
 *
 * @param direction 規則層記錄的副露來源方向。
 * @param playerCount 對局玩家人數，只接受二至四人。
 * @return 用於牌面呈現的方向；兩人對局時，所有非自身方向都呈現為對家。
 * @throws IllegalArgumentException 當 [playerCount] 不在二至四人範圍內時。
 */
fun meldDisplayDirection(direction: RelativeDirection, playerCount: Int): RelativeDirection {
    require(playerCount in 2..4) { "playerCount must be between 2 and 4" }
    return if (playerCount == 2 && direction != RelativeDirection.Self) {
        RelativeDirection.Across
    } else {
        direction
    }
}
