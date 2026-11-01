package com.doublemoon1119.mahjongcraft.platform.minecraft.seating

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證物品放置前後的有效性、同角色防重入及成功放置的保留政策。 */
class SeatItemPlacementCoordinatorTest {
    /** 無效請求不得開始放置或入座。 */
    @Test
    fun `invalid request does not place or seat`() {
        val coordinator = SeatItemPlacementCoordinator<String>()
        val calls = mutableListOf<String>()
        val result = coordinator.attempt(
            key = "actor",
            valid = { false },
            place = {
                calls += "place"
                true
            },
            seat = {
                calls += "seat"
                true
            },
        )
        assertEquals(SeatItemPlacementResult.REJECTED, result)
        assertEquals(emptyList(), calls)
    }

    /** 放置失敗不執行入座。 */
    @Test
    fun `failed placement never attempts seating`() {
        val coordinator = SeatItemPlacementCoordinator<String>()
        val calls = mutableListOf<String>()
        val result = coordinator.attempt(
            key = "actor",
            valid = { true },
            place = {
                calls += "place"
                false
            },
            seat = {
                calls += "seat"
                true
            },
        )
        assertEquals(SeatItemPlacementResult.NOT_PLACED, result)
        assertEquals(listOf("place"), calls)
    }

    /** 同一接管重入不再放置，但不阻擋其他角色。 */
    @Test
    fun `reentrant same actor is blocked while another actor can proceed`() {
        val coordinator = SeatItemPlacementCoordinator<String>()
        val calls = mutableListOf<String>()
        val result = coordinator.attempt(
            key = "actor",
            valid = { true },
            place = {
                assertEquals(
                    SeatItemPlacementResult.BUSY,
                    coordinator.attempt(
                        key = "actor",
                        valid = { error("Reentrant request must not validate") },
                        place = { error("Reentrant request must not place") },
                        seat = { error("Reentrant request must not seat") },
                    ),
                )
                assertEquals(
                    SeatItemPlacementResult.SEATED,
                    coordinator.attempt(
                        key = "other",
                        valid = { true },
                        place = {
                            calls += "other-place"
                            true
                        },
                        seat = {
                            calls += "other-seat"
                            true
                        },
                    ),
                )
                true
            },
            seat = {
                calls += "seat"
                true
            },
        )
        assertEquals(SeatItemPlacementResult.SEATED, result)
        assertEquals(listOf("other-place", "other-seat", "seat"), calls)
    }

    /** 放置期間場次失效時保留座位，不對新場次安排入座。 */
    @Test
    fun `invalidated binding preserves placement without seating`() {
        val coordinator = SeatItemPlacementCoordinator<String>()
        var valid = true
        var placed = 0
        val result = coordinator.attempt(
            key = "actor",
            valid = { valid },
            place = {
                placed++
                valid = false
                true
            },
            seat = { error("Stale binding must not seat") },
        )
        assertEquals(SeatItemPlacementResult.PLACED_UNSEATED, result)
        assertEquals(1, placed)
    }

    /** 入座失敗只回報尚未入座，不重跑已成功的物品放置。 */
    @Test
    fun `seating failure does not retry successful placement`() {
        var placed = 0
        assertEquals(
            SeatItemPlacementResult.PLACED_UNSEATED,
            SeatItemPlacementCoordinator<String>().attempt(
                key = "actor",
                valid = { true },
                place = {
                    placed++
                    true
                },
                seat = { false },
            ),
        )
        assertEquals(1, placed)
    }

    /** 回呼例外向外傳播，並釋放防重入紀錄。 */
    @Test
    fun `throwing callback releases the active actor`() {
        val coordinator = SeatItemPlacementCoordinator<String>()
        assertFailsWith<IllegalStateException> {
            coordinator.attempt(
                key = "actor",
                valid = { true },
                place = { error("Provider failed") },
                seat = { true },
            )
        }
        assertEquals(
            SeatItemPlacementResult.SEATED,
            coordinator.attempt(
                key = "actor",
                valid = { true },
                place = { true },
                seat = { true },
            ),
        )
    }
}
