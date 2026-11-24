package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug.room

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 指定座位開局只移動指定的玩家，其他成員保留原本的相對順序。 */
class DebugRoomCommandTest {
    /** 指定的玩家放到指定座位，其他成員依原本順序坐其餘座位。 */
    @Test
    fun `seat order moves only the chosen player`() {
        val (first, second, third, fourth) = List(4) { Uuid.random() }
        val room = listOf(first, second, third, fourth)
        assertEquals(listOf(second, third, first, fourth), DebugRoomCommand.seatOrder(room, first, 2))
        assertEquals(listOf(fourth, first, second, third), DebugRoomCommand.seatOrder(room, fourth, 0))
        assertEquals(room, DebugRoomCommand.seatOrder(room, third, 2))
    }

    /** 不在房間裡的玩家或不存在的座位都拒絕。 */
    @Test
    fun `seat order rejects outsiders and missing seats`() {
        val room = List(4) { Uuid.random() }
        assertFailsWith<IllegalArgumentException> { DebugRoomCommand.seatOrder(room, Uuid.random(), 0) }
        assertFailsWith<IllegalArgumentException> { DebugRoomCommand.seatOrder(room, room.first(), 4) }
    }
}
