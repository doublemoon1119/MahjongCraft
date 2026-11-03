package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.flow.common.room.model.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.SeatedParticipantSources
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 排除卡片使用實際來源 UUID，不把原成員編號當成世界實體。 */
class ExcludedParticipantIdentityTest {
    /** 真人、世界角色與未知來源使用各自正確的 UUID 。 */
    @Test
    fun `display identity distinguishes player entity and unknown sources`() {
        val host = Uuid.random()
        val player = Uuid.random()
        val actor = Uuid.random()
        val unknown = Uuid.random()
        val entity = Uuid.random()
        val room = Room(
            id = Uuid.random(),
            hostId = host,
            gameConfig = GameConfig(FakeMahjongRuleConfig()),
            playerIds = listOf(host),
            admission = RoomAdmissionState(
                excludedParticipants = mapOf(
                    RoomParticipantIdentity.player(player) to player,
                    RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, entity) to actor,
                    RoomParticipantIdentity("test:unknown", Uuid.random()) to unknown,
                ),
            ),
        )
        val view = room.toSnapshot(host)
        assertEquals(ExcludedParticipantIdentity(player), ExcludedParticipantIdentity.resolve(view, player))
        assertEquals(ExcludedParticipantIdentity(entity), ExcludedParticipantIdentity.resolve(view, actor))
        assertEquals(ExcludedParticipantIdentity(unknown), ExcludedParticipantIdentity.resolve(view, unknown))
        assertNull(ExcludedParticipantIdentity.resolve(view, Uuid.random()))
        assertNull(ExcludedParticipantIdentity.resolve(room.toSnapshot(Uuid.random()), actor))
    }

    /** 同名角色仍能以 UUID 找到個別對象，搜尋不受大小寫影響。 */
    @Test
    fun `UUID search distinguishes equal names`() {
        val first = ExcludedParticipantIdentity(Uuid.parse("aaaaaaaa-1111-2222-3333-444444444444"))
        val second = ExcludedParticipantIdentity(Uuid.parse("bbbbbbbb-1111-2222-3333-444444444444"))
        assertTrue(first.matches("Same actor", "same"))
        assertTrue(second.matches("Same actor", "same"))
        assertTrue(first.matches("Same actor", "AAAAAAAA"))
        assertFalse(second.matches("Same actor", "AAAAAAAA"))
        assertTrue(second.matches("Same actor", second.id.toString()))
        assertFalse(first.matches("Same actor", second.id.toString()))
    }
}
