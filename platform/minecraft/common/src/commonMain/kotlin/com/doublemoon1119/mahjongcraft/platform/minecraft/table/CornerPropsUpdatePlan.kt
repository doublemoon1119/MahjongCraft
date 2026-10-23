package com.doublemoon1119.mahjongcraft.platform.minecraft.table

/**
 * 一次桌上物件同步時牌區的配合方式。
 *
 * @property slidingSeatWidths 需要依新寬度一起平移的座位與它們的新寬度。
 * @property presentsPropsAfterSlide 是否等桌上所有平移結束才擺上物件；為 false 時先同步物件。為 true 時 [slideId] 一定不是 null。
 * @property slideId 這次更新開始或接手等待的平移；不為 null 時，平台以它呼叫 [TableCornerLayoutPlanner.awaitSlideEnd]
 *           等桌上的牌全部停下。沒有平移也沒有進行中的平移時為 null。
 */
data class CornerPropsUpdatePlan(
    val slidingSeatWidths: Map<Int, Double>,
    val presentsPropsAfterSlide: Boolean,
    val slideId: Long?,
)
