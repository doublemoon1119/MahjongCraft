package com.doublemoon1119.mahjongcraft.logic.rules.taiwan

import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType

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
