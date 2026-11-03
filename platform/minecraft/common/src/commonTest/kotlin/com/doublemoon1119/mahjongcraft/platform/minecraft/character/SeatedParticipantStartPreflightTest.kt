package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameConfig
import com.doublemoon1119.mahjongcraft.flow.common.room.model.Room
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomAdmissionState
import com.doublemoon1119.mahjongcraft.flow.common.room.model.RoomParticipantIdentity
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import com.doublemoon1119.mahjongcraft.testing.logic.config.FakeMahjongRuleConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 開局檢查採用當下全部成員的入座與資格，拒絕缺漏或另一來源的描述。 */
class SeatedParticipantStartPreflightTest {
    /** 已確認的房主。 */
    private val host = Uuid.random()

    /** 已自動入房的角色。 */
    private val actor = Uuid.random()

    /** 同一個原生來源。 */
    private val identity = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())

    /** 已記錄來源的等待房間。 */
    private val room = Room(
        id = Uuid.random(),
        hostId = host,
        gameConfig = GameConfig(FakeMahjongRuleConfig()),
        playerIds = listOf(host, actor),
        admission = RoomAdmissionState(memberIdentities = mapOf(actor to identity)),
    )

    /** 只作為已驗證公開描述，不執行任何原生工廠。 */
    private val appearance = AiCharacterSnapshot(BuiltInAiCharacters.TILE_MASCOT_ID, BuiltInAiCharacters.TILE_MASCOT, "Current actor")

    /** 離座、未知、缺資料或資格已失效時，不能沿用之前已入房的結果。 */
    @Test
    fun `start requires current seating eligibility and the exact source`() {
        assertNull(SeatedParticipantStartPreflight.capture(room, emptyMap()))
        for (state in SeatState.entries.filterNot { it == SeatState.SEATED }) {
            assertNull(SeatedParticipantStartPreflight.capture(room, mapOf(actor to SeatedParticipantObservation(identity, state, appearance))))
        }
        assertNull(SeatedParticipantStartPreflight.capture(room, mapOf(actor to SeatedParticipantObservation(identity, SeatState.SEATED, null))))
        val other = RoomParticipantIdentity(SeatedParticipantSources.WORLD_ENTITY, Uuid.random())
        assertNull(SeatedParticipantStartPreflight.capture(room, mapOf(actor to SeatedParticipantObservation(other, SeatState.SEATED, appearance))))
        val captures = SeatedParticipantStartPreflight.capture(room, mapOf(actor to SeatedParticipantObservation(identity, SeatState.SEATED, appearance)))!!
        assertEquals(listOf(AiCharacterParticipantCapture(room.admission.instanceId, actor, identity, appearance)), captures)
    }

    /** 名稱是目前的公開外觀，不是入座授權或來源識別的依據。 */
    @Test
    fun `renaming a seated participant keeps the same verified identity`() {
        val renamed = appearance.copy(displayName = "Renamed actor")
        val capture = SeatedParticipantStartPreflight.capture(room, mapOf(actor to SeatedParticipantObservation(identity, SeatState.SEATED, renamed)))!!
        assertEquals(listOf(AiCharacterParticipantCapture(room.admission.instanceId, actor, identity, renamed)), capture)
    }
}
