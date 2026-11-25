package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.development

import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinCelebrationRequest
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.uuid.Uuid

/** 登記的 [WinCelebrationRequestAdjuster]。沒有登記時演出請求保持不變。 */
class WinCelebrationRequestAdjusters {
    /** 依登記順序套用的調整。 */
    private val adjusters = CopyOnWriteArrayList<WinCelebrationRequestAdjuster>()

    /**
     * 登記一個調整。
     *
     * @param adjuster 要登記的調整。
     */
    fun register(adjuster: WinCelebrationRequestAdjuster) {
        adjusters += adjuster
    }

    /**
     * 依登記順序套用所有調整。必須在判斷演出是否暫停整桌操作之前套用，讓兩者依據同一份請求。
     *
     * @param tableId 演出所屬的牌桌。
     * @param request 原始的演出請求。
     * @return 套用全部調整後的請求。
     */
    fun adjust(tableId: Uuid, request: WinCelebrationRequest): WinCelebrationRequest = adjusters.fold(request) { current, adjuster -> adjuster.adjust(tableId, current) }
}
