package com.doublemoon1119.mahjongcraft.logic.module

import kotlin.uuid.Uuid

/**
 * 規則決定的場上供託與連莊點數歸屬。
 *
 * @property stickPotShares 每位收款者分到的供託；收款者必須是候選玩家，金額不可為負，總額必須等於這次收下的供託。
 * 只有整場結束時可以回傳空 map，表示剩下的供託不歸任何人。
 * @property comboBonusRecipientIds 收取連莊點數的玩家，每位的金額由 [MahjongRuleModule.resolveComboBonusPayments]
 * 決定；必須是候選玩家，整場結束時必須為空。
 */
data class SettlementExtrasAllocation(
    val stickPotShares: Map<Uuid, Int>,
    val comboBonusRecipientIds: Set<Uuid>,
)
