package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry

/** 保證角色先取得安全位置，才生成及重新驗證入座；不維護第二套碰撞規則。 */
object AiCharacterSeatActivation {
    /**
     * 依固定順序啟用一個角色。
     *
     * @param ownership 已驗證的所有權。
     * @param preview 使用共用座位協調器查詢安全位置，不改動世界。
     * @param position 將尚未生成的自有角色放到已確認位置。
     * @param spawn 自有角色加入世界的入口；借用角色不呼叫。
     * @param seat 重新驗證並完成入座或安全站立。
     * @return 成功完成入座或站立時為 true；失敗由租用協調器清理。
     */
    fun activate(
        ownership: AiCharacterOwnership,
        preview: () -> SeatGeometry?,
        position: (SeatGeometry) -> Unit,
        spawn: () -> Boolean,
        seat: () -> Boolean,
    ): Boolean {
        val target = preview() ?: return false
        if (ownership == AiCharacterOwnership.OWNED) {
            position(target)
            if (!spawn()) return false
        }
        return seat()
    }
}
