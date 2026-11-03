package com.doublemoon1119.mahjongcraft.platform.fabric.server.character

import net.minecraft.entity.Entity

/** 將原生卸載與已確認離開分開，不能因區塊暫時不可用而刪除房間成員。 */
internal object FabricParticipantRemovalPolicy {
    /**
     * 已死亡、明確移除或跨維度代表離開原席位，其他卸載原因保持未知。
     *
     * @param reason 原生移除原因，尚未移除時為 null。
     * @return 有足夠依據移除等待室關聯時為 true，不授權刪除進行中名冊。
     */
    fun isConfirmedDeparture(reason: Entity.RemovalReason?): Boolean = reason == Entity.RemovalReason.KILLED || reason == Entity.RemovalReason.DISCARDED || reason == Entity.RemovalReason.CHANGED_DIMENSION
}
