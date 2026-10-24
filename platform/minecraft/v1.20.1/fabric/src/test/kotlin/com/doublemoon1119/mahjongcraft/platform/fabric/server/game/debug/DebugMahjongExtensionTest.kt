package com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug

import com.doublemoon1119.mahjongcraft.flow.common.game.event.GameEventPublicMarkerResolverRegistry
import com.doublemoon1119.mahjongcraft.flow.server.game.riichi.RiichiGameEventPublicMarkerResolver
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.presentation.DebugWinRoundContinuationState
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugAutoNorthRiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugNotenDeclarationRiichiRuleModule
import com.doublemoon1119.mahjongcraft.platform.fabric.server.game.debug.rule.DebugWinnerDealerRiichiRuleModule
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

/** 驗證 debug 日麻變體會登記公開事件標記解析器。 */
class DebugMahjongExtensionTest {
    /** 三個 debug 日麻變體都使用 Riichi 的公開事件標記解析器，且不會登記其他規則。 */
    @Test
    fun `debug riichi variants register public event marker resolvers`() {
        val registry = GameEventPublicMarkerResolverRegistry()

        DebugMahjongExtension(DebugWinRoundContinuationState()).registerGameEventPublicMarkerResolvers(registry)

        assertEquals(
            setOf(
                DebugAutoNorthRiichiRuleModule.ID,
                DebugWinnerDealerRiichiRuleModule.ID,
                DebugNotenDeclarationRiichiRuleModule.ID,
            ),
            registry.registrationKeys,
        )
        assertSame(RiichiGameEventPublicMarkerResolver, registry.find(DebugAutoNorthRiichiRuleModule.ID))
        assertSame(RiichiGameEventPublicMarkerResolver, registry.find(DebugWinnerDealerRiichiRuleModule.ID))
        assertSame(RiichiGameEventPublicMarkerResolver, registry.find(DebugNotenDeclarationRiichiRuleModule.ID))
    }
}
