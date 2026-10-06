package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

/** 規則中立的內建胡牌結算模板 key。 */
object BuiltInWinSettlementTemplateKeys {
    /** 規則中立的通用模板；規則沒有綁定模板時使用。 */
    const val GENERIC: String = "${MinecraftModMetadata.MOD_ID}:generic"
}

/** 規則中立的內建胡牌結算欄位。 */
object BuiltInWinSettlementFieldIds {
    val OUTCOME_TITLE = field("outcome_title")
    val WINNER_SUMMARY = field("winner_summary")
    val WINNER_IDENTITY = field("winner_identity")
    val RESPONSIBLE_PLAYER_IDENTITY = field("responsible_player_identity")
    val RELATION_ARROW = field("relation_arrow")
    val DORA_LABEL = field("dora_label")
    val URA_DORA_LABEL = field("ura_dora_label")
    val COMPLETE_HAND = field("complete_hand")
    val COMPLETE_HAND_GROUPS = field("complete_hand_groups")
    val WINNING_TILE = field("winning_tile")
    val PAYMENT_SUMMARY = field("payment_summary")
    val TOTAL_SCORE = field("total_score")

    private fun field(path: String) = PresentationFieldId("${MinecraftModMetadata.MOD_ID}:$path")
}

/** 內建結算模板共用的牌面 asset key。 */
object BuiltInWinSettlementTileAssets {
    /** 現有麻將牌背貼圖；用於尚未公開的指示牌占位。 */
    const val TILE_BACK = "back"
}

/** 規則中立的內建胡牌結算模板。 */
object BuiltInWinSettlementTemplates {
    private val BACKGROUND = PresentationContainerStyle(backgroundArgb = 0xCC101722.toInt(), padding = 8f)

    /** 通用 fallback 模板；不包含寶牌、翻數、符數等規則專屬概念。 */
    val GENERIC: WinSettlementPresentationTemplate = WinSettlementPresentationTemplate(
        key = BuiltInWinSettlementTemplateKeys.GENERIC,
        root = PresentationLayout.Column(
            children = listOf(
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.OUTCOME_TITLE),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.WINNER_SUMMARY),
                PresentationLayout.Row(
                    children = listOf(
                        PresentationLayout.TileList(BuiltInWinSettlementFieldIds.COMPLETE_HAND),
                        PresentationLayout.Spacer(width = 5f),
                        PresentationLayout.Tile(BuiltInWinSettlementFieldIds.WINNING_TILE),
                    ),
                    spacing = 2f,
                ),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY),
                PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
            ),
            spacing = 4f,
            style = BACKGROUND,
        ),
    )
}
