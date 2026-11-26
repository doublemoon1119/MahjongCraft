package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatState
import kotlin.test.Test
import kotlin.test.assertEquals

/** 等待位置與正式座位順序無關，未知來源不等於已離座。 */
class SeatedParticipantPresenceTest {
    /** 四個標準等待席位任一處確認乘坐，都可保留房間成員。 */
    @Test
    fun `any table side can hold a waiting participant`() {
        for (side in 0..3) {
            val states = List(4) { if (it == side) SeatState.SEATED else SeatState.INVALID }
            assertEquals(SeatedParticipantPresence.PRESENT, SeatedParticipantPresence.resolve(states))
        }
    }

    /** 全部沒有乘坐才是離座，失敗或缺資料時保留成員。 */
    @Test
    fun `absence requires every table side to be known`() {
        assertEquals(SeatedParticipantPresence.DEPARTED, SeatedParticipantPresence.resolve(List(4) { SeatState.INVALID }))
        assertEquals(SeatedParticipantPresence.UNKNOWN, SeatedParticipantPresence.resolve(emptyList()))
        assertEquals(SeatedParticipantPresence.UNKNOWN, SeatedParticipantPresence.resolve(listOf(SeatState.SEATED, SeatState.FAILED)))
    }
}
