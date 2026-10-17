package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** 驗證流局玩家狀態會依完整原因 ID 與狀態 ID 選擇用語。 */
class ExhaustiveDrawSettlementStatusTextTest {
    /** 以第三方原因登記的結算用語。 */
    private val registry = ExhaustiveDrawReasonDisplayNameRegistryImpl().apply {
        register(
            ExhaustiveDrawSettlementStatusLabelsDefinition(
                reasonId = "example:normal",
                labels = ExhaustiveDrawSettlementStatusLabels(
                    beneficiaryTranslationKey = "example.status.beneficiary",
                    othersTranslationKey = "example.status.others",
                ),
            ),
        )
    }

    /** 沒有登記原因用語時，不把聽牌或未知狀態套成預設日麻文字。 */
    @Test
    fun `unknown reason and missing status have no invented label`() {
        listOf(null, BuiltInExhaustiveDrawSettlementStatusIds.TENPAI, BuiltInExhaustiveDrawSettlementStatusIds.NOTEN, "example:unknown").forEach { statusId ->
            assertNull(
                exhaustiveDrawSettlementStatusTranslationKey(
                    reasonId = "other:normal",
                    statusId = statusId,
                    registry = registry,
                ),
            )
        }
    }

    /** 已登記原因的內建聽牌與未聽狀態使用該原因的結算用語。 */
    @Test
    fun `registered reason uses its settlement labels`() {
        assertEquals(
            "example.status.beneficiary",
            exhaustiveDrawSettlementStatusTranslationKey(
                reasonId = "example:normal",
                statusId = BuiltInExhaustiveDrawSettlementStatusIds.TENPAI,
                registry = registry,
            ),
        )
        assertEquals(
            "example.status.others",
            exhaustiveDrawSettlementStatusTranslationKey(
                reasonId = "example:normal",
                statusId = BuiltInExhaustiveDrawSettlementStatusIds.NOTEN,
                registry = registry,
            ),
        )
    }

    /** 同一路徑的狀態名稱不會跨 namespace 套用日麻用語。 */
    @Test
    fun `same path in another namespace is not treated as built in status`() {
        assertNull(
            exhaustiveDrawSettlementStatusTranslationKey(
                reasonId = "example:normal",
                statusId = "other:tenpai",
                registry = registry,
            ),
        )
    }

    /** 內建流局宣告狀態只接受完整內建 ID。 */
    @Test
    fun `draw declaration requires the built in status id`() {
        assertEquals(
            MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_DRAW_DECLARATION,
            exhaustiveDrawSettlementStatusTranslationKey(
                reasonId = "example:declaration",
                statusId = BuiltInExhaustiveDrawSettlementStatusIds.DRAW_DECLARATION,
                registry = registry,
            ),
        )
        assertNull(
            exhaustiveDrawSettlementStatusTranslationKey(
                reasonId = "example:declaration",
                statusId = "other:draw_declaration",
                registry = registry,
            ),
        )
    }
}
