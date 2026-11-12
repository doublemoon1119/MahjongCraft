package com.doublemoon1119.mahjongcraft.logic.rules.riichi

import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentProjectionContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentReadContext
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.uuid.Uuid

/** 驗證日麻獨立玩家組件的公開契約與換局處理。 */
class RiichiPlayerComponentsTest {
    /** 包牌責任不依賴整包玩家狀態，可由公開投影交給任一合法讀取者。 */
    @Test
    fun `pao liability projects independently of declaration state`() {
        val registry = registry()
        val liability = RiichiPaoLiability(RiichiPaoYaku.Daisangen, RelativeDirection.Left)
        val player = FakeMahjongPlayerFactory.create(
            components = ComponentSet.EMPTY.with(RiichiPlayerComponents.PAO_LIABILITY, liability),
        )
        val state = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())
        val readers = listOf(ComponentReadContext.Player(player.id), ComponentReadContext.Spectator(Uuid.random()), ComponentReadContext.Public)
        for (reader in readers) {
            val views = registry.project(
                context = ComponentProjectionContext(
                    ruleModuleId = "test:riichi",
                    tableState = state,
                    ownerType = BuiltInComponentOwners.PLAYER,
                    owner = player,
                    reader = reader,
                ),
                components = player.components,
            )
            assertEquals(liability, views[RiichiPlayerComponentViews.PAO_LIABILITY])
        }
    }

    /** 永久振聽的存在只向本人提供，公共投影與其他玩家都不包含此項。 */
    @Test
    fun `permanent furiten is absent from every non owner projection`() {
        val registry = registry()
        val player = FakeMahjongPlayerFactory.create(
            components = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit),
        )
        val state = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())
        val context = ComponentProjectionContext(
            ruleModuleId = "test:riichi",
            tableState = state,
            ownerType = BuiltInComponentOwners.PLAYER,
            owner = player,
            reader = ComponentReadContext.Player(player.id),
        )
        assertEquals(Unit, registry.project(context, player.components)[RiichiPlayerComponentViews.PERMANENT_FURITEN])
        val otherReaders = listOf(ComponentReadContext.Player(Uuid.random()), ComponentReadContext.Spectator(player.id), ComponentReadContext.Public)
        for (reader in otherReaders) {
            val views = registry.project(context.copy(reader = reader), player.components)
            assertNull(views[RiichiPlayerComponentViews.PERMANENT_FURITEN])
            assertEquals(emptyList(), views.types)
        }
    }

    /** 未成立時不建立空責任；換局清除上一局的責任。 */
    @Test
    fun `pao absence is explicit and a new round clears liability`() {
        val registry = registry()
        val player = FakeMahjongPlayerFactory.create()
        val state = FakeTableStateFactory.create(players = listOf(player), config = RiichiRuleConfig())
        val context = ComponentLifecycleContext(
            ruleModuleId = "test:riichi",
            tableState = state,
            ownerType = BuiltInComponentOwners.PLAYER,
            owner = player,
            previousTableState = null,
        )
        assertNull(registry.initialize(context)[RiichiPlayerComponents.PAO_LIABILITY])
        val previous = ComponentSet.EMPTY.with(RiichiPlayerComponents.PAO_LIABILITY, RiichiPaoLiability(RiichiPaoYaku.Daisuushii, RelativeDirection.Right))
        assertNull(registry.transitionRound(context.copy(previousTableState = state), previous)[RiichiPlayerComponents.PAO_LIABILITY])
        assertEquals(RiichiPaoYaku.Daisuushii, previous[RiichiPlayerComponents.PAO_LIABILITY]?.yaku)
    }

    /** 建立只包含日麻玩家組件的登記。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        registerRiichiPlayerComponents(this)
        freeze()
    }
}
