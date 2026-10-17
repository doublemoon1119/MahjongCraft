package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInExhaustiveDrawSettlementStatusIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys

/**
 * 依完整流局原因與玩家狀態選擇結算身分的翻譯 key。
 *
 * 只有內建狀態 ID 或該流局原因已登記的結算用語會產生文字；未知 namespace 的同名狀態不會套用日麻用語。
 *
 * @param reasonId 流局原因的完整識別碼。
 * @param statusId 玩家狀態的完整識別碼；沒有可顯示狀態時為 null。
 * @param registry 已登記的流局原因與結算身分用語。
 * @return 對應的翻譯 key；未知或未登記時為 null。
 */
fun exhaustiveDrawSettlementStatusTranslationKey(
    reasonId: String,
    statusId: String?,
    registry: ExhaustiveDrawReasonDisplayNameRegistry,
): String? = when (statusId) {
    BuiltInExhaustiveDrawSettlementStatusIds.DRAW_DECLARATION ->
        MinecraftMessageKeys.EXHAUSTIVE_DRAW_SETTLEMENT_STATUS_DRAW_DECLARATION

    BuiltInExhaustiveDrawSettlementStatusIds.TENPAI ->
        registry.findSettlementStatusLabels(reasonId)?.beneficiaryTranslationKey

    BuiltInExhaustiveDrawSettlementStatusIds.NOTEN ->
        registry.findSettlementStatusLabels(reasonId)?.othersTranslationKey

    else -> null
}
