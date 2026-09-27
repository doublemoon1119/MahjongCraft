package com.doublemoon1119.mahjongcraft.platform.minecraft.table

import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDynamicState
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.registerBuiltInRiichiRoundInfoLineDisplays
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證局況顯示行的翻譯資訊註冊流程。 */
class RoundInfoLineDisplayRegistryTest {
    /** 驗證內建日麻與第三方 key 都透過同一個 registry 解析。 */
    @Test
    fun `built-in and third-party keys resolve registered displays`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            registerBuiltInRiichiRoundInfoLineDisplays()
            register("example:custom", RoundInfoLineDisplay("example.message.custom"))
        }

        val title = registry.find("riichiTitle")
        assertEquals(true, title != null)
        assertEquals("example.message.custom", registry.find("example:custom")?.translationKey)
        assertNull(registry.find("example:unknown"))
    }

    /** 日麻呈現端依桌況建立完整三行，不再要求 logic 模組組裝面板內容。 */
    @Test
    fun `riichi provider builds ordered lines from table state`() {
        val state = FakeTableStateFactory.create(
            tileWall = TileWall(List(50) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, 5)) }),
            prevalentWind = Wind.SOUTH,
            roundNumber = 2,
            comboCount = 1,
            dynamicRuleState = RiichiDynamicState(riichiStickCount = 2),
        )
        val registry = RoundInfoLineDisplayRegistryImpl().apply { registerBuiltInRiichiRoundInfoLineDisplays() }

        assertEquals(
            listOf(
                RoundInfoLine(
                    "riichiTitle",
                    listOf(
                        RoundInfoArgument.WindValue(Wind.SOUTH),
                        RoundInfoArgument.Number(state.localRoundNumber),
                        RoundInfoArgument.Number(1),
                    ),
                ),
                RoundInfoLine("riichiStickPot", listOf(RoundInfoArgument.Number(2))),
                RoundInfoLine("riichiWallRemaining", listOf(RoundInfoArgument.Number(50))),
            ),
            registry.buildLines(BuiltInRuleModuleIds.RIICHI, state),
        )
    }

    /** 未註冊的規則沒有局況行；第三方 provider 可自行決定行與順序。 */
    @Test
    fun `custom and missing providers are independent of riichi`() {
        val state = FakeTableStateFactory.create()
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            register("example:custom", RoundInfoLineDisplay("example.message.custom"))
            register("example:rule", RoundInfoLineProvider { listOf(RoundInfoLine("example:custom", listOf(RoundInfoArgument.Number(it.comboCount)))) })
        }

        assertEquals(listOf(RoundInfoLine("example:custom", listOf(RoundInfoArgument.Number(state.comboCount)))), registry.buildLines("example:rule", state))
        assertEquals(emptyList(), registry.buildLines("example:missing", state))
    }

    /** 驗證 registry 凍結後禁止第三方延遲修改。 */
    @Test
    fun `frozen registry rejects late registration`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply { freeze() }

        assertFailsWith<IllegalStateException> {
            registry.register("example:late", RoundInfoLineDisplay("example.message.late"))
        }
    }

    /** 驗證重複登記同一個 key 會被拒絕，避免第三方無意間覆蓋內建顯示。 */
    @Test
    fun `duplicate key registration is rejected`() {
        val registry = RoundInfoLineDisplayRegistryImpl().apply {
            register("example:duplicate", RoundInfoLineDisplay("example.message.a"))
        }

        assertFailsWith<IllegalArgumentException> {
            registry.register("example:duplicate", RoundInfoLineDisplay("example.message.b"))
        }
    }
}
