package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry

/** 自有角色先取得安全位置才生成；借用角色的回座行為由提供者決定。 */
object AiCharacterSeatActivation {
    /**
     * 依固定順序啟用一個角色。
     *
     * @param ownership 已驗證的所有權。
     * @param preview 自有角色使用共用座位協調器查詢安全位置，不改動世界。
     * @param position 將尚未生成的自有角色放到已確認位置。
     * @param spawn 自有角色加入世界的入口；借用角色不呼叫。
     * @param seat 自有角色重新驗證並完成入座或安全站立；借用角色不呼叫。
     * @return 借用角色可交由提供者處理，或自有角色已完成入座／站立時為 true；失敗由租用協調器清理。
     */
    fun activate(
        ownership: AiCharacterOwnership,
        preview: () -> SeatGeometry?,
        position: (SeatGeometry) -> Unit,
        spawn: () -> Boolean,
        seat: () -> Boolean,
    ): Boolean {
        if (ownership == AiCharacterOwnership.BORROWED) return true
        val target = preview() ?: return false
        position(target)
        if (!spawn()) return false
        return seat()
    }
}
