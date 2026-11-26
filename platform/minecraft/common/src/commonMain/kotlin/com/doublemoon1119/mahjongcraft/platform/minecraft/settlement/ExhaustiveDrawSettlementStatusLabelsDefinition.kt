package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 流局結算身分用語的登記描述。
 *
 * @property reasonId 流局原因的穩定識別碼。
 * @property labels 得分玩家與其他玩家的身分用語。
 */
data class ExhaustiveDrawSettlementStatusLabelsDefinition(
    val reasonId: String,
    val labels: ExhaustiveDrawSettlementStatusLabels,
)
