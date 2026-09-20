package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.flow.network.dto.message.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import net.minecraft.text.Text
import org.koin.core.annotation.Provided
import org.koin.core.annotation.Single

/**
 * 依決策提示指定的規則模組，解析決策介面上的用語。
 *
 * 動作與狀態的說法屬於規則（例如榮和與胡牌、捨牌與丟牌），因此一律向 registry 查詢，不在呈現程式裡寫死；
 * 規則沒有登記時退回中立預設，連中立預設都沒有時顯示原始 ID，讓第三方的未登記項目仍看得出是什麼。
 *
 * 規則模組由伺服器隨決策提示送來（[PlayerDecisionPromptDto.ruleModuleId]），不由客戶端自行推算。
 */
@Single
class DecisionTextResolver(
    @Provided private val actionVocabulary: GameActionVocabularyRegistry,
    @Provided private val decisionStatusDisplayNames: DecisionStatusDisplayNameRegistry,
    @Provided private val exhaustiveDrawReasonDisplayNames: ExhaustiveDrawReasonDisplayNameRegistry,
    @Provided private val roundPreparationDisplayNames: RoundPreparationDisplayNameRegistry,
) {
    /**
     * 動作或開局準備選項顯示在操作卡上的名稱。
     *
     * 依序查動作用語、流局原因與開局準備選項——後兩者的 ID 本身就屬於規則，各自已有登記處。
     */
    fun actionLabel(ruleModuleId: String?, actionId: String): Text {
        val translationKey = actionVocabulary.find(ruleModuleId, actionId)?.labelKey
            ?: exhaustiveDrawReasonDisplayNames.find(actionId)
            ?: roundPreparationDisplayNames.find(actionId)
        return translationKey?.let(Text::translatable) ?: Text.literal(actionId)
    }

    /** 動作在操作卡中的順序；未登記時為 null，代表排在所有已登記的動作之後。 */
    fun actionOrder(ruleModuleId: String?, actionId: String): Int? = actionVocabulary.find(ruleModuleId, actionId)?.order

    /** 捨牌分析狀態（振聽、和牌資格）的顯示名稱。 */
    fun statusText(ruleModuleId: String?, statusId: String): Text = decisionStatusDisplayNames.find(ruleModuleId, statusId)
        ?.let(Text::translatable)
        ?: Text.literal(statusId)
}
