package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi

import com.doublemoon1119.mahjongcraft.flow.common.game.model.riichi.RiichiWinSettlementIds
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementFieldIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationAlignment
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationAnimationEffect
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationArrangement
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationContainerStyle
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationFieldId
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationLayout
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTimeline
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationTimelineAnchor
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ScaleKeyframe
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldSnapshot
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys

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

    /** 直接顯示規則提供內容的欄位。 */
    val ENTRY_FIELDS: List<PresentationFieldId> = listOf(YAKU, HAN_FU, YAKUMAN_TOTAL)

    /** 固定顯示五個位置的指示牌欄位。 */
    val INDICATOR_FIELDS: List<PresentationFieldId> = listOf(DORA, URA_DORA)

    private val PANEL = PresentationContainerStyle(
        backgroundArgb = 0xC7000000.toInt(),
        padding = 0f,
    )

    /** 日麻完整模板。 */
    val RIICHI: WinSettlementPresentationTemplate = WinSettlementPresentationTemplate(
        key = RIICHI_KEY,
        root = PresentationLayout.Box(
            width = 320f,
            height = 156f,
            style = PANEL,
            children = listOf(
                positioned(
                    PresentationLayout.Text(OUTCOME_TITLE, scale = 1.35f, argb = 0xFFFFD45A.toInt()),
                    160f,
                    11f,
                ),
                positioned(playerRelationship(), 160f, 29f),
                PresentationLayout.Positioned(
                    PresentationLayout.Row(
                        listOf(
                            PresentationLayout.TileGroups(BuiltInWinSettlementFieldIds.COMPLETE_HAND_GROUPS),
                            PresentationLayout.Spacer(width = 5f),
                            PresentationLayout.Tile(BuiltInWinSettlementFieldIds.WINNING_TILE),
                        ),
                        spacing = 0.6f,
                        arrangement = PresentationArrangement.CENTER,
                    ),
                    x = 160f,
                    y = 51f,
                    horizontalAnchor = PresentationAlignment.CENTER,
                    verticalAnchor = PresentationAlignment.CENTER,
                ),
                positioned(doraIndicators(DORA, URA_DORA), 160f, 70.5f),
                PresentationLayout.Positioned(
                    PresentationLayout.RepeatEntries(YAKU, entriesPerColumn = 4, width = 232f),
                    160f,
                    95f,
                    horizontalAnchor = PresentationAlignment.CENTER,
                ),
                PresentationLayout.Positioned(
                    PresentationLayout.Row(
                        listOf(
                            postEntrySummary(HAN_FU, 0xFFE5E5E5.toInt()),
                            postEntrySummary(YAKUMAN_TOTAL, 0xFFFFC247.toInt()),
                        ),
                    ),
                    44f,
                    141f,
                ),
                PresentationLayout.Positioned(
                    PresentationLayout.Animated(
                        PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE, argb = 0xFFFFD45A.toInt()),
                        // 翻符／役滿那一行錨在逐條揭示之後，佔一條役種的間隔（8 tick）；分數接在它後面出現。
                        PresentationTimeline(PresentationTimelineAnchor.AFTER_ENTRIES, offsetTicks = 8, durationTicks = 18),
                        listOf(PresentationAnimationEffect.Fade(), scoreRevealScale()),
                        transformOriginX = PresentationAlignment.END,
                        transformOriginY = PresentationAlignment.START,
                    ),
                    276f,
                    141f,
                    horizontalAnchor = PresentationAlignment.END,
                ),
            ),
        ),
        detailFieldLabelKeys = mapOf(
            DORA to WinSettlementTextKeys.DORA_INDICATOR,
            URA_DORA to WinSettlementTextKeys.URA_DORA_INDICATOR,
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
            revealed.take(INDICATOR_SLOT_COUNT) +
                List((INDICATOR_SLOT_COUNT - revealed.size).coerceAtLeast(0)) {
                    BuiltInWinSettlementTileAssets.TILE_BACK
                },
        )
    }
}

private fun postEntrySummary(fieldId: PresentationFieldId, argb: Int): PresentationLayout = PresentationLayout.IfPresent(
    fieldId,
    PresentationLayout.Animated(
        PresentationLayout.Text(fieldId, argb = argb),
        PresentationTimeline(PresentationTimelineAnchor.AFTER_ENTRIES, durationTicks = 6),
        listOf(PresentationAnimationEffect.Fade()),
    ),
)

private fun playerRelationship(): PresentationLayout = PresentationLayout.Row(
    children = listOf(
        PresentationLayout.PlayerIdentity(BuiltInWinSettlementFieldIds.WINNER_IDENTITY),
        PresentationLayout.IfPresent(
            BuiltInWinSettlementFieldIds.RESPONSIBLE_PLAYER_IDENTITY,
            PresentationLayout.Row(
                listOf(
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.RELATION_ARROW, argb = 0xFFE5C16A.toInt()),
                    PresentationLayout.PlayerIdentity(BuiltInWinSettlementFieldIds.RESPONSIBLE_PLAYER_IDENTITY),
                ),
                spacing = 5f,
            ),
        ),
    ),
    spacing = 5f,
    arrangement = PresentationArrangement.CENTER,
    fillMaxWidth = true,
)

private fun doraIndicators(dora: PresentationFieldId, uraDora: PresentationFieldId): PresentationLayout = PresentationLayout.Row(
    children = listOf(
        PresentationLayout.Weighted(indicator(BuiltInWinSettlementFieldIds.DORA_LABEL, dora)),
        PresentationLayout.Weighted(indicator(BuiltInWinSettlementFieldIds.URA_DORA_LABEL, uraDora)),
    ),
    arrangement = PresentationArrangement.SPACE_EVENLY,
    fillMaxWidth = true,
)

private fun indicator(label: PresentationFieldId, tiles: PresentationFieldId): PresentationLayout = PresentationLayout.Row(
    children = listOf(
        PresentationLayout.Text(label, scale = 0.82f, argb = 0xFFE5C16A.toInt()),
        PresentationLayout.TileList(tiles, tileWidth = 8f, tileHeight = 11f, spacing = 2f),
    ),
    spacing = 4f,
    arrangement = PresentationArrangement.CENTER,
    fillMaxWidth = true,
)

private fun positioned(child: PresentationLayout, x: Float, y: Float): PresentationLayout.Positioned = PresentationLayout.Positioned(
    child = child,
    x = x,
    y = y,
    horizontalAnchor = PresentationAlignment.CENTER,
)

private fun scoreRevealScale() = PresentationAnimationEffect.ScaleKeyframes(
    listOf(ScaleKeyframe(0f, 1.35f), ScaleKeyframe(1f, 1f)),
)

private const val INDICATOR_SLOT_COUNT = 5
