package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.flow.common.game.model.GameAbortReasonIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException

/**
 * 執行一段套用或驗證規則組件結果的步驟；組件不符合契約時以規則契約失敗中止本局。
 *
 * 未登記、對象不符、不適用目前規則、缺少必要值或預期舊值已過時的組件都屬於這類失敗，
 * 不當成一般非法請求，也不寫入任何一半的結果。
 *
 * @param T 步驟結果型別。
 * @param source 診斷訊息使用的來源名稱。
 * @param block 套用或驗證組件的步驟。
 * @return 步驟結果。
 * @throws RuleContractViolationException 組件不符合契約時。
 */
internal inline fun <T> requireValidRuleComponents(source: String, block: () -> T): T = try {
    block()
} catch (failure: IllegalArgumentException) {
    throw RuleContractViolationException(
        GameAbortReasonIds.RULE_EXECUTION_FAILED,
        "$source produced invalid components: ${failure.message}",
    )
}
