package com.doublemoon1119.mahjongcraft.logic.table.seat

import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.logic.table.opening.WallOpening
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證內建自風 policy 的可變人數分配、開門語意與輸出不變式。 */
class SeatWindAssignmentPolicyTest {
    /** 莊家錨定 policy 在三人桌只分配東、南、西。 */
    @Test
    fun `dealer anchored policy assigns east south west for three players`() {
        val players = List(3) { Uuid.random() }
        val assignment = DealerAnchoredSeatWindAssignmentPolicy.assignValidated(context(players, players[1]))

        assertEquals(Wind.EAST, assignment[players[1]])
        assertEquals(Wind.SOUTH, assignment[players[2]])
        assertEquals(Wind.WEST, assignment[players[0]])
        assertEquals(setOf(Wind.EAST, Wind.SOUTH, Wind.WEST), assignment.values.toSet())
    }

    /** 莊家錨定 policy 在二人桌只分配東、南。 */
    @Test
    fun `dealer anchored policy assigns east south for two players`() {
        val players = List(2) { Uuid.random() }
        val assignment = DealerAnchoredSeatWindAssignmentPolicy.assignValidated(context(players, players[0]))

        assertEquals(mapOf(players[0] to Wind.EAST, players[1] to Wind.SOUTH), assignment)
    }

    /** 四人開門 policy 讓從莊家偏移兩席的開門者取得東。 */
    @Test
    fun `wall opening anchored policy assigns east to opening player`() {
        val players = List(4) { Uuid.random() }
        val assignment = FourPlayerWallOpeningAnchoredSeatWindAssignmentPolicy.assignValidated(
            context(players, players[0], WallOpening(wallSideOffsetFromDealer = 2, stacksFromRight = 3)),
        )

        assertEquals(Wind.EAST, assignment[players[2]])
        assertEquals(Wind.SOUTH, assignment[players[3]])
        assertEquals(Wind.WEST, assignment[players[0]])
        assertEquals(Wind.NORTH, assignment[players[1]])
    }

    /** 四面牌牆的開門 offset 不可直接套到三人桌。 */
    @Test
    fun `wall opening anchored policy rejects three players`() {
        val players = List(3) { Uuid.random() }

        assertFailsWith<IllegalArgumentException> {
            FourPlayerWallOpeningAnchoredSeatWindAssignmentPolicy.assignValidated(
                context(players, players[0], WallOpening(wallSideOffsetFromDealer = 1, stacksFromRight = 3)),
            )
        }
    }

    /** 第三方 policy 可以為三人開門規則提供自己的映射。 */
    @Test
    fun `custom three player policy can use wall opening context`() {
        val players = List(3) { Uuid.random() }
        val policy = SeatWindAssignmentPolicy { assignmentContext ->
            val eastIndex = assignmentContext.wallOpening!!.wallSideOffsetFromDealer
            List(players.size) { offset ->
                players[(eastIndex + offset) % players.size] to Wind.entries[offset]
            }.toMap()
        }

        val assignment = policy.assignValidated(
            context(players, players[0], WallOpening(wallSideOffsetFromDealer = 1, stacksFromRight = 3)),
        )

        assertEquals(Wind.EAST, assignment[players[1]])
    }

    /** 驗證 helper 會拒絕遺漏玩家、陌生玩家或重複風位的規則結果。 */
    @Test
    fun `validated assignment rejects malformed policy output`() {
        val players = List(3) { Uuid.random() }
        val duplicateWindPolicy = SeatWindAssignmentPolicy {
            mapOf(players[0] to Wind.EAST, players[1] to Wind.EAST, players[2] to Wind.SOUTH)
        }
        val missingPlayerPolicy = SeatWindAssignmentPolicy {
            mapOf(players[0] to Wind.EAST, Uuid.random() to Wind.SOUTH, players[2] to Wind.WEST)
        }

        assertFailsWith<IllegalArgumentException> { duplicateWindPolicy.assignValidated(context(players, players[0])) }
        assertFailsWith<IllegalArgumentException> { missingPlayerPolicy.assignValidated(context(players, players[0])) }
    }

    /** 建立測試用自風指派上下文。 */
    private fun context(
        players: List<Uuid>,
        dealerPlayerId: Uuid,
        wallOpening: WallOpening? = null,
    ): SeatWindAssignmentContext = SeatWindAssignmentContext(
        playerIdsInTurnOrder = players,
        dealerPlayerId = dealerPlayerId,
        diceRoll = null,
        wallOpening = wallOpening,
    )
}
