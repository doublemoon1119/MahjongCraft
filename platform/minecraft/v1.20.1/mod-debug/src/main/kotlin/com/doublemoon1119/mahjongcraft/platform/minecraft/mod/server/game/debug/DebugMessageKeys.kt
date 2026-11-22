package com.doublemoon1119.mahjongcraft.platform.minecraft.mod.server.game.debug

/** debug 工具顯示給玩家的訊息翻譯鍵。 */
object DebugMessageKeys {
    /** 已入座測試角色已建立，由正式掃描加入。 */
    const val SEATED_PARTICIPANT_CREATED: String = "mahjongcraft.message.debug.seated_participant.created"

    /** 測試需要房主等待室與可用家具座位。 */
    const val SEATED_PARTICIPANT_UNAVAILABLE: String = "mahjongcraft.message.debug.seated_participant.unavailable"

    /** debug 決策畫面文字卡片的標題。 */
    const val DECISION_TEXT_CARD_LABEL: String = "mahjongcraft.message.debug.decision.text_card.label"

    /** debug 決策畫面文字卡片的內文。 */
    const val DECISION_TEXT_CARD_BODY: String = "mahjongcraft.message.debug.decision.text_card.body"

    /** debug 決策畫面文字卡片的提示文字。 */
    const val DECISION_TEXT_CARD_HINT: String = "mahjongcraft.message.debug.decision.text_card.hint"

    /** debug 決策畫面文字卡片的說明文字。 */
    const val DECISION_TEXT_CARD_DESCRIPTION: String = "mahjongcraft.message.debug.decision.text_card.description"

    /** 開發用自動拔北三人日麻的規則名稱。 */
    const val RULE_MODULE_AUTO_NORTH_RIICHI: String = "mahjongcraft.message.debug.rule_module.auto_north_riichi"

    /** 開發用和牌者坐莊、每局換位四人日麻的規則名稱。 */
    const val RULE_MODULE_WINNER_DEALER_RIICHI: String = "mahjongcraft.message.debug.rule_module.winner_dealer_riichi"

    /** 開發用可宣告不聽四人日麻的規則名稱。 */
    const val RULE_MODULE_NOTEN_DECLARATION_RIICHI: String = "mahjongcraft.message.debug.rule_module.noten_declaration_riichi"

    /** 開發用可宣告不聽規則中，流局時公開聽牌的選項名稱。 */
    const val DECISION_REVEAL_TENPAI_LABEL: String = "mahjongcraft.message.debug.decision.reveal_tenpai.label"

    /** 開發用可宣告不聽規則中，流局時公開聽牌的選項說明。 */
    const val DECISION_REVEAL_TENPAI_DESCRIPTION: String = "mahjongcraft.message.debug.decision.reveal_tenpai.description"

    /** 開發用可宣告不聽規則中，流局時宣告不聽的選項與動作名稱。 */
    const val DECISION_DECLARE_NOTEN_LABEL: String = "mahjongcraft.message.debug.decision.declare_noten.label"

    /** 開發用可宣告不聽規則中，流局時宣告不聽的選項說明。 */
    const val DECISION_DECLARE_NOTEN_DESCRIPTION: String = "mahjongcraft.message.debug.decision.declare_noten.description"
}
