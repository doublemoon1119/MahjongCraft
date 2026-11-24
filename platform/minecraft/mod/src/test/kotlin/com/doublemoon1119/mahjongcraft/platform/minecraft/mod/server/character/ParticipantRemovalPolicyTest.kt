package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.character

import net.minecraft.world.entity.Entity
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** 驗證 native 移除訊號不把卸載當成退出。 */
class ParticipantRemovalPolicyTest {
    /** 未載入、隨玩家卸載與沒有原因都不足以確認離開。 */
    @Test
    fun `unloading never confirms room departure`() {
        assertFalse(ParticipantRemovalPolicy.isConfirmedDeparture(null))
        assertFalse(ParticipantRemovalPolicy.isConfirmedDeparture(Entity.RemovalReason.UNLOADED_TO_CHUNK))
        assertFalse(ParticipantRemovalPolicy.isConfirmedDeparture(Entity.RemovalReason.UNLOADED_WITH_PLAYER))
    }

    /** 死亡、明確移除與轉移維度都是對原席位的確定離開訊號。 */
    @Test
    fun `death discard and dimension changes confirm departure`() {
        assertTrue(ParticipantRemovalPolicy.isConfirmedDeparture(Entity.RemovalReason.KILLED))
        assertTrue(ParticipantRemovalPolicy.isConfirmedDeparture(Entity.RemovalReason.DISCARDED))
        assertTrue(ParticipantRemovalPolicy.isConfirmedDeparture(Entity.RemovalReason.CHANGED_DIMENSION))
    }
}
