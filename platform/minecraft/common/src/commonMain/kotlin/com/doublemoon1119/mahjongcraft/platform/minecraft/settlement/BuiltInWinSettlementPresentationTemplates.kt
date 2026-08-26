package com.doublemoon1119.mahjongcraft.platform.minecraft.settlement

import com.doublemoon1119.mahjongcraft.flow.common.game.model.BuiltInRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.WinSettlementTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.metadata.MinecraftModMetadata

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

/** 內建通用 fallback 模板；不包含寶牌、翻數、符數等規則專屬概念。 */
fun WinSettlementPresentationTemplateRegistry.registerBuiltInWinSettlementTemplates() {
    val background = PresentationContainerStyle(backgroundArgb = 0xCC101722.toInt(), padding = 8f)
    registerTemplate(
        WinSettlementPresentationTemplate(
            key = "${MinecraftModMetadata.MOD_ID}:generic",
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
                style = background,
            ),
        ),
    )
    registerTemplate(
        WinSettlementPresentationTemplate(
            key = "${MinecraftModMetadata.MOD_ID}:nagashi_mangan",
            root = PresentationLayout.Column(
                children = listOf(
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.OUTCOME_TITLE),
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.WINNER_SUMMARY),
                    PresentationLayout.RepeatEntries(PresentationFieldId("${MinecraftModMetadata.MOD_ID}:riichi_yaku")),
                    PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE),
                ),
                spacing = 6f,
                style = background,
            ),
        ),
    )
    registerFieldProvider(BuiltInWinSettlementFieldIds.COMPLETE_HAND) { snapshot ->
        PresentationValue.TileListValue(snapshot.tileAssetKeys)
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.COMPLETE_HAND_GROUPS) { snapshot ->
        PresentationValue.TileGroupsValue(snapshot.tileAssetGroups)
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.OUTCOME_TITLE) { snapshot ->
        PresentationValue.TextValue(
            when {
                snapshot.outcomeId == BuiltInRoundOutcomeIds.NAGASHI_MANGAN -> WinSettlementTranslationKeys.NAGASHI_MANGAN
                snapshot.isTsumo -> WinSettlementTranslationKeys.TSUMO
                else -> WinSettlementTranslationKeys.RON
            },
        )
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.WINNER_SUMMARY) { snapshot ->
        if (snapshot.isTsumo || snapshot.outcomeId == BuiltInRoundOutcomeIds.NAGASHI_MANGAN) {
            PresentationValue.TextValue("%s", listOf(snapshot.winnerDisplayName))
        } else {
            PresentationValue.TextValue(
                WinSettlementTranslationKeys.RON_RELATIONSHIP,
                listOf(snapshot.winnerDisplayName, snapshot.responsiblePlayerDisplayName.orEmpty()),
            )
        }
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.WINNER_IDENTITY) { snapshot ->
        PresentationValue.PlayerIdentityValue(snapshot.winnerId, snapshot.winnerDisplayName, snapshot.winnerIsAi)
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.RESPONSIBLE_PLAYER_IDENTITY) { snapshot ->
        snapshot.responsiblePlayerId?.let { id ->
            PresentationValue.PlayerIdentityValue(
                id,
                snapshot.responsiblePlayerDisplayName.orEmpty(),
                snapshot.responsiblePlayerIsAi == true,
            )
        }
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.RELATION_ARROW) { snapshot ->
        snapshot.responsiblePlayerId?.let { PresentationValue.TextValue(WinSettlementTranslationKeys.RELATIONSHIP_ARROW) }
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.DORA_LABEL) {
        PresentationValue.TextValue(WinSettlementTranslationKeys.DORA)
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.URA_DORA_LABEL) {
        PresentationValue.TextValue(WinSettlementTranslationKeys.URA_DORA)
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY) { null }
    registerFieldProvider(BuiltInWinSettlementFieldIds.WINNING_TILE) { snapshot ->
        snapshot.winningTileAssetKey?.let(PresentationValue::TileValue)
    }
    registerFieldProvider(BuiltInWinSettlementFieldIds.TOTAL_SCORE) { snapshot ->
        PresentationValue.TextValue(WinSettlementTranslationKeys.TOTAL_SCORE, listOf(snapshot.totalScore.toString()))
    }
    registerBuiltInRiichiWinSettlementTemplate()
}

/** Bundled 日麻以通用文字／牌面原語組成的完整模板。 */
private fun WinSettlementPresentationTemplateRegistry.registerBuiltInRiichiWinSettlementTemplate() {
    val yaku = PresentationFieldId("${MinecraftModMetadata.MOD_ID}:riichi_yaku")
    val hanFu = PresentationFieldId("${MinecraftModMetadata.MOD_ID}:riichi_han_fu")
    val yakumanTotal = PresentationFieldId("${MinecraftModMetadata.MOD_ID}:riichi_yakuman_total")
    val dora = PresentationFieldId("${MinecraftModMetadata.MOD_ID}:riichi_dora")
    val uraDora = PresentationFieldId("${MinecraftModMetadata.MOD_ID}:riichi_ura_dora")
    val panel = PresentationContainerStyle(
        backgroundArgb = 0xC7000000.toInt(),
        padding = 0f,
    )
    registerTemplate(
        WinSettlementPresentationTemplate(
            key = "${MinecraftModMetadata.MOD_ID}:riichi",
            root = PresentationLayout.Box(
                width = 320f,
                height = 156f,
                style = panel,
                children = listOf(
                    positioned(
                        PresentationLayout.Text(BuiltInWinSettlementFieldIds.OUTCOME_TITLE, scale = 1.35f, argb = 0xFFFFD45A.toInt()),
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
                    positioned(doraIndicators(dora, uraDora), 160f, 70.5f),
                    PresentationLayout.Positioned(
                        PresentationLayout.RepeatEntries(yaku, entriesPerColumn = 4, width = 232f),
                        160f,
                        95f,
                        horizontalAnchor = PresentationAlignment.CENTER,
                    ),
                    PresentationLayout.Positioned(
                        PresentationLayout.Row(
                            listOf(
                                postEntrySummary(hanFu, 0xFFE5E5E5.toInt()),
                                postEntrySummary(yakumanTotal, 0xFFFFC247.toInt()),
                            ),
                        ),
                        44f,
                        141f,
                    ),
                    PresentationLayout.Positioned(
                        PresentationLayout.Animated(
                            PresentationLayout.Text(BuiltInWinSettlementFieldIds.TOTAL_SCORE, argb = 0xFFFFD45A.toInt()),
                            PresentationTimeline(PresentationTimelineAnchor.SCORE_REVEAL, durationTicks = 18),
                            listOf(PresentationAnimationEffect.Fade(), legacyScoreScale()),
                            transformOriginX = PresentationAlignment.END,
                            transformOriginY = PresentationAlignment.START,
                        ),
                        276f,
                        141f,
                        horizontalAnchor = PresentationAlignment.END,
                    ),
                ),
            ),
        ),
    )
    listOf(yaku, hanFu, yakumanTotal).forEach { id ->
        registerFieldProvider(id) { snapshot -> snapshot.extensionField(id) }
    }
    listOf(dora, uraDora).forEach { id ->
        registerFieldProvider(id) { snapshot ->
            val revealed = (snapshot.extensionField(id) as? PresentationValue.TileListValue)?.assetKeys.orEmpty()
            PresentationValue.TileListValue(
                revealed.take(INDICATOR_SLOT_COUNT) +
                    List((INDICATOR_SLOT_COUNT - revealed.size).coerceAtLeast(0)) {
                        BuiltInWinSettlementTileAssets.TILE_BACK
                    },
            )
        }
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

private fun legacyScoreScale() = PresentationAnimationEffect.ScaleKeyframes(
    listOf(ScaleKeyframe(0f, 1.35f), ScaleKeyframe(1f, 1f)),
)

private const val INDICATOR_SLOT_COUNT = 5
