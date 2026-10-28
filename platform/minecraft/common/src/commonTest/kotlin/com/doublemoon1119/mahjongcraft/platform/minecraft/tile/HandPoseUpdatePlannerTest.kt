package com.doublemoon1119.mahjongcraft.platform.minecraft.tile

import com.doublemoon1119.mahjongcraft.logic.module.HandTilePose
import com.doublemoon1119.mahjongcraft.logic.module.HandTileState
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證逐張姿態只在成功套用後更新，普通刷新與其他玩家不會互相影響。 */
class HandPoseUpdatePlannerTest {
    /** 一張牌改變仍會刷新，不會只記住玩家曾經攤牌。 */
    @Test
    fun `individual pose changes retry until applied and unchanged updates do not refresh`() {
        val planner = HandPoseUpdatePlanner()
        val table = Uuid.random()
        val player = Uuid.random()
        val first = HandTileState(Uuid.random(), HandTilePose.FACE_UP)
        val second = HandTileState(Uuid.random(), HandTilePose.FACE_UP)
        val initial = mapOf(player to listOf(first))
        assertEquals(emptySet(), planner.changedPlayers(table, mapOf(player to emptyList())))
        assertEquals(setOf(player), planner.changedPlayers(table, initial))
        assertEquals(setOf(player), planner.changedPlayers(table, initial))
        planner.recordApplied(table, player, listOf(first))
        assertEquals(emptySet(), planner.changedPlayers(table, initial))
        assertEquals(setOf(player), planner.changedPlayers(table, mapOf(player to listOf(first, second))))
        planner.recordApplied(table, player, listOf(first, second))
        assertEquals(setOf(player), planner.changedPlayers(table, mapOf(player to listOf(second))))
    }

    /** 一位玩家成功不會清掉另一位的待更新姿態，回復直立也可重試。 */
    @Test
    fun `applying one player leaves another pending and upright state can be restored`() {
        val planner = HandPoseUpdatePlanner()
        val table = Uuid.random()
        val first = Uuid.random()
        val second = Uuid.random()
        val state = HandTileState(Uuid.random(), HandTilePose.FACE_UP)
        planner.recordApplied(table, first, listOf(state))
        assertEquals(setOf(second), planner.changedPlayers(table, mapOf(first to listOf(state), second to listOf(state))))
        assertEquals(setOf(first), planner.changedPlayers(table, mapOf(first to emptyList())))
        planner.recordApplied(table, first, emptyList())
        assertEquals(emptySet(), planner.changedPlayers(table, mapOf(first to emptyList())))
    }

    /** 新局與 session 重設不沿用舊姿態，其他桌子保持獨立。 */
    @Test
    fun `round and session cleanup reset only their intended records`() {
        val planner = HandPoseUpdatePlanner()
        val first = Uuid.random()
        val second = Uuid.random()
        val player = Uuid.random()
        val state = listOf(HandTileState(Uuid.random(), HandTilePose.FACE_UP))
        planner.recordApplied(first, player, state)
        planner.recordApplied(second, player, state)
        planner.clear(first)
        assertEquals(setOf(player), planner.changedPlayers(first, mapOf(player to state)))
        assertEquals(emptySet(), planner.changedPlayers(second, mapOf(player to state)))
        planner.reset()
        assertEquals(setOf(player), planner.changedPlayers(second, mapOf(player to state)))
    }
}
