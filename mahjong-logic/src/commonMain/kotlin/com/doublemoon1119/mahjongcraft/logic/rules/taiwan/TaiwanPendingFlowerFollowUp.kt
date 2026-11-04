package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import kotlin.uuid.Uuid

/**
 * 一張已公開、尚待規則決定後續處理的花牌。
 *
 * @property playerId 公開花牌的玩家。
 * @property tileId 已移到公開花區的牌張識別碼。
 */
data class TaiwanPendingFlowerFollowUp(
    val playerId: Uuid,
    val tileId: Uuid,
)

/** 台灣麻將桌況使用的私有流程組件。 */
object TaiwanTableComponents {
    /** 摸牌後已公開、尚未完成後續處理的花牌。 */
    val PENDING_FLOWER_FOLLOW_UP: ComponentType<TaiwanPendingFlowerFollowUp> = ComponentType(
        id = "mahjongcraft:taiwan/table/pending_flower_follow_up",
        valueClass = TaiwanPendingFlowerFollowUp::class,
        copyValue = { it.copy() },
        target = BuiltInComponentOwners.TABLE,
    )
}
