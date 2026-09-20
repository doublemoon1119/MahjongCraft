package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiGameAction
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證操作卡順序由登記決定：核心動作為吃、碰、槓、榮和、自摸，槓的三種型態同序。 */
class DecisionHudActionDisplayOrderTest {
    private val texts = testDecisionTextResolver()

    /** 依登記的順序排列後，核心動作應精確符合吃、碰、槓、榮和、自摸。 */
    @Test
    fun `sorting shuffled built-in actions restores chi pon kan ron tsumo order`() {
        val shuffled = listOf(
            BuiltInGameActionIds.TSUMO,
            BuiltInGameActionIds.KAN_OPEN,
            BuiltInGameActionIds.RON,
            BuiltInGameActionIds.CHI,
            BuiltInGameActionIds.PON,
        )

        val sorted = shuffled.sortedBy { texts.actionOrder(BuiltInRuleModuleIds.RIICHI, it) }

        assertEquals(
            listOf(
                BuiltInGameActionIds.CHI,
                BuiltInGameActionIds.PON,
                BuiltInGameActionIds.KAN_OPEN,
                BuiltInGameActionIds.RON,
                BuiltInGameActionIds.TSUMO,
            ),
            sorted,
        )
    }

    /** 三種槓型態必須同序，才能與碰、榮和維持穩定的相對順序。 */
    @Test
    fun `every kan variant shares the same order`() {
        val orders = listOf(BuiltInGameActionIds.KAN_OPEN, BuiltInGameActionIds.KAN_CLOSED, BuiltInGameActionIds.KAN_ADDED)
            .map { texts.actionOrder(BuiltInRuleModuleIds.RIICHI, it) }

        assertTrue(orders.distinct().size == 1, "kan variants must share one order: $orders")
    }

    /** 日麻登記的動作排在核心動作之後，立直在九種九牌之前。 */
    @Test
    fun `riichi actions sort after the built-in ones`() {
        val tsumo = texts.actionOrder(BuiltInRuleModuleIds.RIICHI, BuiltInGameActionIds.TSUMO)
        val riichi = texts.actionOrder(BuiltInRuleModuleIds.RIICHI, RiichiGameAction.Riichi.id)
        val kyuushuKyuuhai = texts.actionOrder(BuiltInRuleModuleIds.RIICHI, "mahjongcraft:kyuushu_kyuuhai")

        assertTrue(tsumo != null && riichi != null && kyuushuKyuuhai != null, "expected every built-in action to be registered")
        assertTrue(tsumo!! < riichi!! && riichi < kyuushuKyuuhai!!, "expected tsumo < riichi < kyuushu kyuuhai: $tsumo, $riichi, $kyuushuKyuuhai")
    }

    /** 沒有登記順序的動作（例如第三方規則的特殊動作）由呼叫端排在最後。 */
    @Test
    fun `an unregistered action has no order`() {
        assertNull(texts.actionOrder(BuiltInRuleModuleIds.RIICHI, "example:special"))
    }
}
