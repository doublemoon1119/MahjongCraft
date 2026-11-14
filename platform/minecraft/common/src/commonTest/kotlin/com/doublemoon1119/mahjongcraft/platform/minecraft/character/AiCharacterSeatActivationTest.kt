package com.doublemoon1119.mahjongcraft.platform.minecraft.character

import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterOwnership
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.seating.SeatGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證正式原生入口所用的安全生成順序及恢復名冊，不使用世界 mock。 */
class AiCharacterSeatActivationTest {
    /** 安全位置尚未確認前不能生成或建立乘坐關係。 */
    @Test
    fun `blocked preview never spawns or seats an actor`() {
        val events = mutableListOf<String>()
        val result = AiCharacterSeatActivation.activate(
            ownership = AiCharacterOwnership.OWNED,
            preview = {
                events += "preview"
                null
            },
            position = { events += "position" },
            spawn = {
                events += "spawn"
                true
            },
            seat = {
                events += "seat"
                true
            },
        )
        assertFalse(result)
        assertEquals(listOf("preview"), events)
    }

    /** 自有角色須先定位、再生成、最後重新驗證入座。 */
    @Test
    fun `owned actor is positioned before spawning and seating`() {
        val events = mutableListOf<String>()
        val result = AiCharacterSeatActivation.activate(
            ownership = AiCharacterOwnership.OWNED,
            preview = {
                events += "preview"
                SeatGeometry(1.0, 64.0, 2.0, 90f)
            },
            position = { events += "position" },
            spawn = {
                events += "spawn"
                true
            },
            seat = {
                events += "seat"
                true
            },
        )
        assertTrue(result)
        assertEquals(listOf("preview", "position", "spawn", "seat"), events)
    }

    /** 借用角色不檢查目的地或執行入座，讓提供者自行決定如何回座。 */
    @Test
    fun `borrowed actor is never spawned or directly moved`() {
        val events = mutableListOf<String>()
        assertTrue(
            actual = AiCharacterSeatActivation.activate(
                ownership = AiCharacterOwnership.BORROWED,
                preview = {
                    error("Borrowed actor does not require a seat preview")
                },
                position = { error("Borrowed actor must not be directly moved") },
                spawn = { error("Borrowed actor must not be spawned") },
                seat = {
                    error("Borrowed actor must not be automatically seated")
                },
            ),
        )
        assertEquals(emptyList(), events)
    }

    /** 生成被世界拒絕後不繼續建立座位支撐。 */
    @Test
    fun `spawn rejection does not invoke seating`() {
        assertFalse(
            actual = AiCharacterSeatActivation.activate(
                ownership = AiCharacterOwnership.OWNED,
                preview = { SeatGeometry(1.0, 64.0, 2.0, 90f) },
                position = {},
                spawn = { false },
                seat = { error("No seating after rejected spawn") },
            ),
        )
    }

    /** 一般恢復必須保留真人的空位，並且完全不查詢在線玩家。 */
    @Test
    fun `actor recovery preserves full seat indices without resolving humans`() {
        val human = Uuid.random()
        val absent = Uuid.random()
        val ai = Uuid.random()
        val riders = AiCharacterSeatRiders.resolve(
            roster = listOf(human, absent, ai),
            includePlayers = false,
            player = { error("Recovery must not move online players") },
            character = { if (it == ai) "actor" else null },
        )
        assertEquals(listOf(null, null, "actor"), riders)
    }

    /** 明確開局入座才解析真人，AI 仍維持原索引。 */
    @Test
    fun `explicit seating resolves human and actor from one complete roster`() {
        val human = Uuid.random()
        val ai = Uuid.random()
        assertEquals(
            expected = listOf("human", "actor"),
            actual = AiCharacterSeatRiders.resolve(
                roster = listOf(human, ai),
                includePlayers = true,
                player = { if (it == human) "human" else null },
                character = { if (it == ai) "actor" else null },
            ),
        )
    }
}
