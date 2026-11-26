package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.GameAction
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.uuid.Uuid

/** 驗證供託與本局進度各自建立、保留、重設及裁切。 */
class RiichiTableComponentsTest {
    /** 第一局以零點建立供託，換局保留供託並清除補牌與待公開項目。 */
    @Test
    fun `round transition retains points but resets wall progress`() {
        val registry = registry()
        val state = FakeTableStateFactory.create(config = RiichiRuleConfig())
        val context = ComponentLifecycleContext(
            ruleModuleId = "test:riichi",
            tableState = state,
            ownerType = BuiltInComponentOwners.TABLE,
            owner = state,
            previousTableState = null,
            random = null,
        )
        val initial = registry.initialize(context)
        assertEquals(0, initial[RiichiTableComponents.POT_POINTS])
        assertEquals(RiichiWallProgress(), initial[RiichiTableComponents.WALL_PROGRESS])
        val previous = initial
            .with(RiichiTableComponents.POT_POINTS, 3000)
            .with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = RiichiWallProgress(
                    completedSupplementalDrawCount = 1,
                    pendingKanDoraReveals = listOf(RiichiPendingKanDoraReveal(Uuid.random(), GameAction.KanType.ADDED_KAN, 1)),
                    completedNorthDrawCount = 2,
                ),
            )
        val next = registry.transitionRound(context.copy(previousTableState = state), previous)
        assertEquals(3000, next[RiichiTableComponents.POT_POINTS])
        assertEquals(RiichiWallProgress(), next[RiichiTableComponents.WALL_PROGRESS])
        assertEquals(1, previous[RiichiTableComponents.WALL_PROGRESS]?.completedSupplementalDrawCount)
    }

    /** 必要供託缺值代表不完整桌況，不在換局時補成零點。 */
    @Test
    fun `round transition rejects a missing required pot`() {
        val registry = registry()
        val state = FakeTableStateFactory.create(config = RiichiRuleConfig())
        assertFailsWith<IllegalArgumentException> {
            registry.transitionRound(
                context = ComponentLifecycleContext(
                    ruleModuleId = "test:riichi",
                    tableState = state,
                    ownerType = BuiltInComponentOwners.TABLE,
                    owner = state,
                    previousTableState = state,
                    random = null,
                ),
                previous = ComponentSet.EMPTY.with(RiichiTableComponents.WALL_PROGRESS, RiichiWallProgress()),
            )
        }
    }

    /** 公開投影只有點數與已完成進度，等待中的動作來源不出現在輸出值。 */
    @Test
    fun `public progress excludes pending reveal details and detaches its source`() {
        val registry = registry()
        val pending = mutableListOf(RiichiPendingKanDoraReveal(Uuid.random(), GameAction.KanType.OPEN_KAN, 1))
        val components = ComponentSet.EMPTY
            .with(RiichiTableComponents.POT_POINTS, 2000)
            .with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = RiichiWallProgress(completedSupplementalDrawCount = 1, pendingKanDoraReveals = pending),
            )
        pending.clear()
        val state = FakeTableStateFactory.create(config = RiichiRuleConfig()).copy(components = components)
        assertEquals(1, components[RiichiTableComponents.WALL_PROGRESS]?.pendingKanDoraReveals?.size)
        val views = registry.project(
            context = ComponentProjectionContext(
                ruleModuleId = "test:riichi",
                tableState = state,
                ownerType = BuiltInComponentOwners.TABLE,
                owner = state,
                reader = ComponentReadContext.Public,
            ),
            components = components,
        )
        assertEquals(2000, views[RiichiTableComponentViews.POT_POINTS])
        assertEquals(1, views.types.size)
    }

    /** 點數必須符合供託單位，指示牌進度不能超前補牌或重複排入同一次槓。 */
    @Test
    fun `component values reject invalid points and inconsistent progress`() {
        listOf(-1000, 1, 1500).forEach { points ->
            assertFailsWith<IllegalArgumentException> { ComponentSet.EMPTY.with(RiichiTableComponents.POT_POINTS, points) }
        }
        assertFailsWith<IllegalArgumentException> {
            ComponentSet.EMPTY.with(RiichiTableComponents.WALL_PROGRESS, RiichiWallProgress(revealedKanDoraCount = 1))
        }
        val pending = RiichiPendingKanDoraReveal(Uuid.random(), GameAction.KanType.ADDED_KAN, 1)
        assertFailsWith<IllegalArgumentException> {
            ComponentSet.EMPTY.with(
                type = RiichiTableComponents.WALL_PROGRESS,
                value = RiichiWallProgress(completedSupplementalDrawCount = 1, pendingKanDoraReveals = listOf(pending, pending)),
            )
        }
    }

    /** 建立只包含日麻桌況契約的獨立登記。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        registerRiichiTableComponents(this)
        freeze()
    }
}
