package com.doublemoon1119.mahjongcraft.platform.fabric.api.character

import com.doublemoon1119.mahjongcraft.logic.base.NamespacedId

/**
 * 初始化期間登記的實體參加能力；高優先權先辨識，同分依完整 ID 排序。
 *
 * @property id 能力的完整識別碼，不作為實體身分。
 * @property provider 唯讀辨識與授權回呼。
 * @property priority 優先權，數值越大越先。
 */
class FabricRoomParticipantDefinition(
    val id: String,
    val provider: FabricRoomParticipantProvider,
    val priority: Int = 0,
) {
    init {
        NamespacedId.requireValid(id) { "Invalid room participant provider ID" }
    }
}
