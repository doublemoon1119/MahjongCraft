package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.StandardWinSettlementLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate

/** 日麻的胡牌結算版面：日麻完整模板與日麻專屬欄位；流局滿貫也使用完整模板。 */
internal object RiichiWinSettlementTemplates {
    /** 日麻完整模板的 key。 */
    val RIICHI_KEY: String = MahjongCraftMetadata.id("riichi/win_settlement")

    /** 結算標題欄位：自摸、榮和或流局滿貫。 */
    val OUTCOME_TITLE: PresentationFieldId = PresentationFieldId(MahjongCraftMetadata.id("riichi/outcome_title"))

    /** 胡牌者摘要欄位：自摸與流局滿貫只列胡牌者，榮和另列放銃者。 */
    val WINNER_SUMMARY: PresentationFieldId = PresentationFieldId(MahjongCraftMetadata.id("riichi/winner_summary"))

    /** 役種列表欄位。 */
    val YAKU: PresentationFieldId = PresentationFieldId(RiichiWinSettlementIds.YAKU_FIELD)

    /** 翻符合計欄位。 */
    val HAN_FU: PresentationFieldId = PresentationFieldId(RiichiWinSettlementIds.HAN_FU_FIELD)

    /** 役滿倍數合計欄位。 */
    val YAKUMAN_TOTAL: PresentationFieldId = PresentationFieldId(RiichiWinSettlementIds.YAKUMAN_TOTAL_FIELD)

    /** 寶牌指示牌欄位。 */
    val DORA: PresentationFieldId = PresentationFieldId(RiichiWinSettlementIds.DORA_FIELD)

    /** 裏寶牌指示牌欄位。 */
    val URA_DORA: PresentationFieldId = PresentationFieldId(RiichiWinSettlementIds.URA_DORA_FIELD)

    /** 寶牌指示牌列的標題欄位。 */
    val DORA_LABEL: PresentationFieldId = PresentationFieldId(MahjongCraftMetadata.id("riichi/dora_label"))

    /** 裏寶牌指示牌列的標題欄位。 */
    val URA_DORA_LABEL: PresentationFieldId = PresentationFieldId(MahjongCraftMetadata.id("riichi/ura_dora_label"))

    /** 直接顯示規則提供內容的欄位。 */
    val ENTRY_FIELDS: List<PresentationFieldId> = listOf(YAKU, HAN_FU, YAKUMAN_TOTAL)

    /** 固定顯示五個位置的指示牌欄位。 */
    val INDICATOR_FIELDS: List<PresentationFieldId> = listOf(DORA, URA_DORA)

    /** 日麻完整模板：規則專屬列是寶牌與裏寶牌指示牌。 */
    val RIICHI: WinSettlementPresentationTemplate = WinSettlementPresentationTemplate(
        key = RIICHI_KEY,
        root = StandardWinSettlementLayout.root(
            title = OUTCOME_TITLE,
            ruleRow = doraIndicators(DORA, URA_DORA),
            entries = YAKU,
            summaries = listOf(
                StandardWinSettlementLayout.summary(HAN_FU),
                StandardWinSettlementLayout.summary(YAKUMAN_TOTAL, argb = 0xFFFFC247.toInt()),
            ),
        ),
        detailFieldLabelKeys = mapOf(
            DORA to RiichiTranslationKeys.SETTLEMENT_DORA_INDICATOR,
            URA_DORA to RiichiTranslationKeys.SETTLEMENT_URA_DORA_INDICATOR,
        ),
    )

    /**
     * 指示牌欄位的值：已公開的指示牌在前，不足 [INDICATOR_SLOT_COUNT] 張的位置補上牌背。
     *
     * @param snapshot 胡牌結算欄位快照。
     * @param fieldId [DORA] 或 [URA_DORA]。
     * @return 固定張數的牌列。
     */
    fun indicatorTiles(
        snapshot: WinSettlementPresentationFieldSnapshot,
        fieldId: PresentationFieldId,
    ): PresentationValue {
        val revealed = (snapshot.extensionField(fieldId) as? PresentationValue.TileListValue)?.assetKeys.orEmpty()
        return PresentationValue.TileListValue(
            assetKeys = revealed.take(INDICATOR_SLOT_COUNT) +
                List((INDICATOR_SLOT_COUNT - revealed.size).coerceAtLeast(0)) {
                    BuiltInWinSettlementTileAssets.TILE_BACK
                },
        )
    }
}

/** 建立寶牌與裏寶牌指示牌的等寬欄位列。 */
private fun doraIndicators(dora: PresentationFieldId, uraDora: PresentationFieldId): PresentationLayout = PresentationLayout.Row(
    children = listOf(
        PresentationLayout.Weighted(StandardWinSettlementLayout.labeledTiles(RiichiWinSettlementTemplates.DORA_LABEL, StandardWinSettlementLayout.smallTiles(dora))),
        PresentationLayout.Weighted(StandardWinSettlementLayout.labeledTiles(RiichiWinSettlementTemplates.URA_DORA_LABEL, StandardWinSettlementLayout.smallTiles(uraDora))),
    ),
    arrangement = PresentationArrangement.SPACE_EVENLY,
    fillMaxWidth = true,
)

/** 每種指示牌固定預留的顯示位置數。 */
private const val INDICATOR_SLOT_COUNT = 5
