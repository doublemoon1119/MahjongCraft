package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

/**
 * 流局時玩家結算身分的用語。
 *
 * @property beneficiaryTranslationKey 在這次流局中得分的玩家，例如日麻一般荒牌流局的「聽牌」。
 * @property othersTranslationKey 其他玩家，例如日麻一般荒牌流局的「未聽」。
 */
data class ExhaustiveDrawSettlementStatusLabels(
    val beneficiaryTranslationKey: String,
    val othersTranslationKey: String,
) {
    init {
        require(beneficiaryTranslationKey.isNotBlank() && othersTranslationKey.isNotBlank()) {
            "Settlement status translation keys must not be blank"
        }
    }
}
