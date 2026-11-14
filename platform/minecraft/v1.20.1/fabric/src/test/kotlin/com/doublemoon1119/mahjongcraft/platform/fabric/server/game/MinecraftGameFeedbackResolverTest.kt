package com.doublemoon1119.mahjongcraft.platform.fabric.server.game

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameError
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftPlayerFeedback
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證對局錯誤轉成玩家回饋時保留決定用語所需的規則。 */
class MinecraftGameFeedbackResolverTest {
    /** 牌摸完的回饋帶著這局的規則，讓提示能用規則自己的牌堆說法。 */
    @Test
    fun `wall exhausted feedback carries the rule of the match`() {
        assertEquals(
            expected = MinecraftPlayerFeedback.WallExhausted(BuiltInRuleModuleIds.RIICHI),
            actual = MinecraftGameFeedbackResolver.actionError(GameError.WallExhausted(Uuid.random()), BuiltInRuleModuleIds.RIICHI),
        )
    }
}
