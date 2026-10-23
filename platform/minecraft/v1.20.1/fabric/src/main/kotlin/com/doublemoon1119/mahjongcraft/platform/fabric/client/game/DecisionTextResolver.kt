package com.doublemoon1119.mahjongcraft.platform.fabric.client.game

import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.PlayerDecisionPromptDto
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistry
import net.minecraft.text.Text

/**
 * 依決策提示指定的規則模組，解析決策介面上的用語。
 *
 * 動作與狀態的說法屬於規則（例如榮和與胡牌、捨牌與丟牌），因此一律向 registry 查詢，不在呈現程式裡寫死；
 * 規則沒有登記時退回中立預設，連中立預設都沒有時顯示原始 ID，讓第三方的未登記項目仍看得出是什麼。
 *
 * 規則模組由伺服器隨決策提示送來（[PlayerDecisionPromptDto.ruleModuleId]），不由客戶端自行推算。
 *
 * @property actionVocabulary 規則專屬動作用語。
 * @property decisionStatusDisplayNames 決策狀態的顯示名稱。
 * @property exhaustiveDrawReasonDisplayNames 流局原因的顯示名稱。
 * @property ruleDecisionDisplayNames 規則選擇選項的顯示名稱。
 */
class DecisionTextResolver(
    private val actionVocabulary: GameActionVocabularyRegistry,
    private val decisionStatusDisplayNames: DecisionStatusDisplayNameRegistry,
    private val exhaustiveDrawReasonDisplayNames: ExhaustiveDrawReasonDisplayNameRegistry,
    private val ruleDecisionDisplayNames: RuleDecisionDisplayNameRegistry,
) {
    /**
     * 動作或規則選擇選項顯示在操作卡上的名稱。
     *
     * 依序查動作用語、流局原因與規則選擇選項——後兩者的 ID 本身就屬於規則，各自已有登記處。
     *
     * @param ruleModuleId 決策所屬規則；null 時使用中立用語。
     * @param actionId 動作或規則選擇選項識別碼。
     * @return 已登記的本地化名稱；未登記時顯示原始識別碼。
     */
    fun actionLabel(ruleModuleId: String?, actionId: String): Text {
        val translationKey = actionVocabulary.find(ruleModuleId, actionId)?.labelKey
            ?: exhaustiveDrawReasonDisplayNames.find(actionId)
            ?: ruleDecisionDisplayNames.find(actionId)
        return translationKey?.let(Text::translatable) ?: Text.literal(actionId)
    }

    /**
     * 取得操作卡提示框與選牌提示使用的動作說明。
     *
     * @param ruleModuleId 決策所屬規則；null 時使用中立用語。
     * @param actionId 動作識別碼。
     * @return 本地化說明；未登記說明時為 null。
     */
    fun actionDescription(ruleModuleId: String?, actionId: String): Text? = actionVocabulary.find(ruleModuleId, actionId)
        ?.descriptionKey
        ?.let(Text::translatable)

    /**
     * 取得操作卡中央的簡短說明。
     *
     * @param ruleModuleId 決策所屬規則；null 時使用中立用語。
     * @param actionId 動作識別碼。
     * @return 本地化說明；未登記時為 null。
     */
    fun actionCardBody(ruleModuleId: String?, actionId: String): Text? = actionVocabulary.find(ruleModuleId, actionId)
        ?.cardBodyKey
        ?.let(Text::translatable)

    /**
     * 取得操作卡按鈕附近的補充提示。
     *
     * @param ruleModuleId 決策所屬規則；null 時使用中立用語。
     * @param actionId 動作識別碼。
     * @return 本地化提示；未登記時為 null。
     */
    fun actionCardHint(ruleModuleId: String?, actionId: String): Text? = actionVocabulary.find(ruleModuleId, actionId)
        ?.cardHintKey
        ?.let(Text::translatable)

    /**
     * 取得動作在操作卡中的顯示順序。
     *
     * @param ruleModuleId 決策所屬規則；null 時使用中立用語。
     * @param actionId 動作識別碼。
     * @return 已登記的順序；未登記時為 null，由呼叫端排在已登記動作之後。
     */
    fun actionOrder(ruleModuleId: String?, actionId: String): Int? = actionVocabulary.find(ruleModuleId, actionId)?.order

    /**
     * 取得振聽、和牌資格等捨牌分析狀態的名稱。
     *
     * @param ruleModuleId 決策所屬規則；null 時使用中立用語。
     * @param statusId 狀態識別碼。
     * @return 本地化名稱；未登記時顯示原始識別碼。
     */
    fun statusText(ruleModuleId: String?, statusId: String): Text = decisionStatusDisplayNames.find(ruleModuleId, statusId)
        ?.let(Text::translatable)
        ?: Text.literal(statusId)
}
