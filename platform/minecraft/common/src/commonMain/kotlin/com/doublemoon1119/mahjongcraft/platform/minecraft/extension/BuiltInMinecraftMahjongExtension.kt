package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.logic.judgment.WaitingTileAvailabilityIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyDefaultDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.HUD_ACTION_PREFIX
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.MinecraftKanActionTokenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameDefaultDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.HUD_STATUS_PREFIX
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementFieldIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementTemplates
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationFieldProviderDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.BuiltInMinecraftTileAppearances
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.blackTextOnRedTile
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.redText

/**
 * MahjongCraft 不屬於任何規則的內建 Minecraft 呈現；所有規則中立的內建登記都寫在這裡。
 *
 * [MinecraftMahjongExtensionRegistrar] 在所有 extension 之前登記它，啟動報告將它的登記列為內建來源。
 * 登記方式與其他 extension 相同，不具有覆寫或繞過重複 key 驗證的特權。
 */
object BuiltInMinecraftMahjongExtension : MinecraftMahjongExtension {
    override val id: String = MahjongCraftMetadata.id("builtin")

    override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
        registry.register(BuiltInMinecraftTileAppearances.DEFAULT)
    }

    override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistry) {
        registry.register(AiStrategyDisplayNameDefinition(RandomAiStrategy.KEY, MinecraftMessageKeys.AI_STRATEGY_RANDOM))
        registry.register(AiStrategyDisplayNameDefinition(BuiltInAiStrategyKeys.BEGINNER, MinecraftMessageKeys.AI_STRATEGY_BEGINNER))
        registry.register(AiStrategyDisplayNameDefinition(BuiltInAiStrategyKeys.INTERMEDIATE, MinecraftMessageKeys.AI_STRATEGY_INTERMEDIATE))
        registry.register(AiStrategyDisplayNameDefinition(BuiltInAiStrategyKeys.ADVANCED, MinecraftMessageKeys.AI_STRATEGY_ADVANCED))
    }

    override fun registerAutomaticControlDisplays(registry: AutomaticControlDisplayRegistry) {
        registry.register(
            definition = AutomaticControlDisplayDefinition(
                controlId = BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND,
                display = AutomaticControlDisplay(
                    labelTranslationKey = MinecraftClientConfigScreenKeys.AUTO_SORT_HAND,
                    descriptionTranslationKey = MinecraftClientConfigScreenKeys.AUTO_SORT_HAND_DESCRIPTION,
                    displayOrder = 0,
                ),
            ),
        )
        registry.register(AutomaticControlDisplayDefinition(BuiltInAutomaticControlIds.AUTO_WIN, roundControlDisplay("auto_win", displayOrder = 10)))
        registry.register(AutomaticControlDisplayDefinition(BuiltInAutomaticControlIds.DECLINE_CALLS, roundControlDisplay("decline_calls", displayOrder = 20)))
        registry.register(AutomaticControlDisplayDefinition(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI, roundControlDisplay("auto_tsumogiri", displayOrder = 30)))
    }

    /**
     * 34 種基本牌與未知牌的 asset key 對應的 Unicode「Mahjong Tiles」區塊字元（U+1F000~U+1F02F）。這組字元由
     * `assets/minecraft/font/default.json`（見 `platform/minecraft/common/src/jvmMain/resources`）註冊成自訂 bitmap 字型，
     * 讓文字內容直接顯示成牌面貼圖；規則專屬牌（例如日麻的赤五）由該規則的 extension 登記同一區塊的字元。
     */
    override fun registerTileEmojis(registry: TileEmojiRegistry) {
        registry.register(TileEmojiDefinition("east", "🀀"))
        registry.register(TileEmojiDefinition("south", "🀁"))
        registry.register(TileEmojiDefinition("west", "🀂"))
        registry.register(TileEmojiDefinition("north", "🀃"))
        registry.register(TileEmojiDefinition("red_dragon", "🀄"))
        registry.register(TileEmojiDefinition("green_dragon", "🀅"))
        registry.register(TileEmojiDefinition("white_dragon", "🀆"))
        registry.register(TileEmojiDefinition("m1", "🀇"))
        registry.register(TileEmojiDefinition("m2", "🀈"))
        registry.register(TileEmojiDefinition("m3", "🀉"))
        registry.register(TileEmojiDefinition("m4", "🀊"))
        registry.register(TileEmojiDefinition("m5", "🀋"))
        registry.register(TileEmojiDefinition("m6", "🀌"))
        registry.register(TileEmojiDefinition("m7", "🀍"))
        registry.register(TileEmojiDefinition("m8", "🀎"))
        registry.register(TileEmojiDefinition("m9", "🀏"))
        registry.register(TileEmojiDefinition("s1", "🀐"))
        registry.register(TileEmojiDefinition("s2", "🀑"))
        registry.register(TileEmojiDefinition("s3", "🀒"))
        registry.register(TileEmojiDefinition("s4", "🀓"))
        registry.register(TileEmojiDefinition("s5", "🀔"))
        registry.register(TileEmojiDefinition("s6", "🀕"))
        registry.register(TileEmojiDefinition("s7", "🀖"))
        registry.register(TileEmojiDefinition("s8", "🀗"))
        registry.register(TileEmojiDefinition("s9", "🀘"))
        registry.register(TileEmojiDefinition("p1", "🀙"))
        registry.register(TileEmojiDefinition("p2", "🀚"))
        registry.register(TileEmojiDefinition("p3", "🀛"))
        registry.register(TileEmojiDefinition("p4", "🀜"))
        registry.register(TileEmojiDefinition("p5", "🀝"))
        registry.register(TileEmojiDefinition("p6", "🀞"))
        registry.register(TileEmojiDefinition("p7", "🀟"))
        registry.register(TileEmojiDefinition("p8", "🀠"))
        registry.register(TileEmojiDefinition("p9", "🀡"))
        registry.register(TileEmojiDefinition(UNKNOWN_TILE_ASSET_KEY, "🀯"))
    }

    /**
     * 34 種基本牌的牌面角落標籤，供非中文圈玩家開啟輔助標籤時使用。
     *
     * 內建映射與第三方映射共用 [TileLabelRegistry.register]，不具有覆寫或繞過重複 key 驗證的特權；
     * [UNKNOWN_TILE_ASSET_KEY] 與尚未提供標籤的牌種一律不註冊，呈現端 [TileLabelRegistry.find]
     * 回傳 `null` 時不顯示任何標籤。
     *
     * 顏色規則：牌面本身印刷成紅色的牌（紅中，以及例如日麻的赤五）角落文字用黑色，其餘一律用紅色；只有右上角
     * 標籤，左上角固定不顯示。
     *
     * 文字內容規則：數牌只顯示點數數字，不額外標示花色字母（花色本身已由牌面材質的餅／條／萬圖案表達）；
     * 字牌用單一或兩個字母縮寫（東南西北＝E/S/W/N，紅中＝R，發＝G，白＝Wh——避免跟西風的 `W` 混淆特地用
     * 兩個字母）。
     */
    override fun registerTileLabels(registry: TileLabelRegistry) {
        // 萬子
        registry.register(TileLabelDefinition("m1", redText("1")))
        registry.register(TileLabelDefinition("m2", redText("2")))
        registry.register(TileLabelDefinition("m3", redText("3")))
        registry.register(TileLabelDefinition("m4", redText("4")))
        registry.register(TileLabelDefinition("m5", redText("5")))
        registry.register(TileLabelDefinition("m6", redText("6")))
        registry.register(TileLabelDefinition("m7", redText("7")))
        registry.register(TileLabelDefinition("m8", redText("8")))
        registry.register(TileLabelDefinition("m9", redText("9")))

        // 條子
        registry.register(TileLabelDefinition("s1", redText("1")))
        registry.register(TileLabelDefinition("s2", redText("2")))
        registry.register(TileLabelDefinition("s3", redText("3")))
        registry.register(TileLabelDefinition("s4", redText("4")))
        registry.register(TileLabelDefinition("s5", redText("5")))
        registry.register(TileLabelDefinition("s6", redText("6")))
        registry.register(TileLabelDefinition("s7", redText("7")))
        registry.register(TileLabelDefinition("s8", redText("8")))
        registry.register(TileLabelDefinition("s9", redText("9")))

        // 餅子
        registry.register(TileLabelDefinition("p1", redText("1")))
        registry.register(TileLabelDefinition("p2", redText("2")))
        registry.register(TileLabelDefinition("p3", redText("3")))
        registry.register(TileLabelDefinition("p4", redText("4")))
        registry.register(TileLabelDefinition("p5", redText("5")))
        registry.register(TileLabelDefinition("p6", redText("6")))
        registry.register(TileLabelDefinition("p7", redText("7")))
        registry.register(TileLabelDefinition("p8", redText("8")))
        registry.register(TileLabelDefinition("p9", redText("9")))

        // 風牌
        registry.register(TileLabelDefinition("east", redText("E")))
        registry.register(TileLabelDefinition("south", redText("S")))
        registry.register(TileLabelDefinition("west", redText("W")))
        registry.register(TileLabelDefinition("north", redText("N")))

        // 三元牌：紅中牌面本身印刷成紅色，跟赤牌歸為同一種黑字規則
        registry.register(TileLabelDefinition("red_dragon", blackTextOnRedTile("R")))
        registry.register(TileLabelDefinition("green_dragon", redText("G")))
        registry.register(TileLabelDefinition("white_dragon", redText("Wh")))
    }

    /** 通用 fallback 模板與共用欄位。 */
    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) {
        registry.register(BuiltInWinSettlementTemplates.GENERIC)
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.COMPLETE_HAND) { snapshot ->
                PresentationValue.TileListValue(snapshot.tileAssetKeys)
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.COMPLETE_HAND_GROUPS) { snapshot ->
                PresentationValue.TileGroupsValue(snapshot.tileAssetGroups)
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.OUTCOME_TITLE) { snapshot ->
                PresentationValue.TextValue(if (snapshot.isTsumo) WinSettlementTextKeys.TSUMO else WinSettlementTextKeys.RON)
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.WINNER_SUMMARY) { snapshot ->
                if (snapshot.isTsumo) {
                    PresentationValue.TextValue("%s", listOf(snapshot.winnerDisplayName))
                } else {
                    PresentationValue.TextValue(
                        translationKey = WinSettlementTextKeys.RON_RELATIONSHIP,
                        arguments = listOf(snapshot.winnerDisplayName, snapshot.responsiblePlayerDisplayName.orEmpty()),
                    )
                }
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.WINNER_IDENTITY) { snapshot ->
                PresentationValue.PlayerIdentityValue(snapshot.winnerId, snapshot.winnerDisplayName, snapshot.winnerIsAi)
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.RESPONSIBLE_PLAYER_IDENTITY) { snapshot ->
                snapshot.responsiblePlayerId?.let { id ->
                    PresentationValue.PlayerIdentityValue(
                        playerId = id,
                        displayName = snapshot.responsiblePlayerDisplayName.orEmpty(),
                        isAi = snapshot.responsiblePlayerIsAi == true,
                    )
                }
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.RELATION_ARROW) { snapshot ->
                snapshot.responsiblePlayerId?.let { PresentationValue.TextValue(WinSettlementTextKeys.RELATIONSHIP_ARROW) }
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.DORA_LABEL) {
                PresentationValue.TextValue(WinSettlementTextKeys.DORA)
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.URA_DORA_LABEL) {
                PresentationValue.TextValue(WinSettlementTextKeys.URA_DORA)
            },
        )
        registry.register(WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY) { null })
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.WINNING_TILE) { snapshot ->
                snapshot.winningTileAssetKey?.let(PresentationValue::TileValue)
            },
        )
        registry.register(
            definition = WinSettlementPresentationFieldProviderDefinition(BuiltInWinSettlementFieldIds.TOTAL_SCORE) { snapshot ->
                PresentationValue.TextValue(WinSettlementTextKeys.TOTAL_SCORE, listOf(snapshot.totalScore.toString()))
            },
        )
    }

    override fun registerMatchSettlementPresentationTemplates(registry: MatchSettlementPresentationTemplateRegistry) {
        registry.register(
            template = MatchSettlementPresentationTemplate(
                key = BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY,
                titleTranslationKey = MinecraftMessageKeys.MATCH_SETTLEMENT_TITLE,
            ),
        )
    }

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistry) {
        registry.register(
            definition = PublicPlayerIndicatorDisplayDefinition(
                id = BuiltInPaymentReasonIds.PAO,
                display = PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_PAO, colorRgb = PAO_PAYMENT_REASON_COLOR),
            ),
        )
    }

    /** 核心動作的中立預設用語與操作卡順序；說法不同的規則登記同一個動作 ID 即可覆寫，不需要改動呈現程式。 */
    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistry) {
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.CHI,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + "chi", MinecraftMessageKeys.GAME_ACTION_CHI, order = 0),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.PON,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + "pon", MinecraftMessageKeys.GAME_ACTION_PON, order = 1),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.KAN_OPEN,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + MinecraftKanActionTokenKeys.OPEN, MinecraftMessageKeys.GAME_ACTION_KAN_OPEN, order = 2),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.KAN_CLOSED,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + MinecraftKanActionTokenKeys.CLOSED, MinecraftMessageKeys.GAME_ACTION_KAN_CLOSED, order = 2),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.KAN_ADDED,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + MinecraftKanActionTokenKeys.ADDED, MinecraftMessageKeys.GAME_ACTION_KAN_ADDED, order = 2),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.RON,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + "ron", MinecraftMessageKeys.GAME_ACTION_RON, order = 3),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.TSUMO,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + "tsumo", MinecraftMessageKeys.GAME_ACTION_TSUMO, order = 4),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.PASS,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + "pass", MinecraftMessageKeys.GAME_ACTION_PASS),
            ),
        )
        registry.register(
            definition = GameActionVocabularyDefaultDefinition(
                actionId = BuiltInGameActionIds.DISCARD,
                vocabulary = GameActionVocabulary(HUD_ACTION_PREFIX + "discard", MinecraftMessageKeys.GAME_ACTION_DISCARD),
            ),
        )
    }

    /** 中立預設：和牌資格沒有特殊限制。 */
    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistry) {
        registry.register(DecisionStatusDisplayNameDefaultDefinition(WaitingTileAvailabilityIds.WIN_AVAILABLE, HUD_STATUS_PREFIX + "win_availability.available"))
    }
}

/**
 * 只在本局有效的內建自動操作的顯示設定；名稱與說明的 translation key 由 [translationId] 組成。
 *
 * @param translationId 名稱與說明翻譯鍵使用的識別片段。
 * @param displayOrder 自動操作在清單中的排序值。
 * @return 對應本局自動操作的顯示設定。
 */
private fun roundControlDisplay(translationId: String, displayOrder: Int): AutomaticControlDisplay {
    val prefix = "mahjongcraft.automatic_control.$translationId"
    return AutomaticControlDisplay(prefix, "$prefix.description", displayOrder)
}

/** 包牌付款原因的文字顏色；與分數增減的紅綠色區隔。 */
private const val PAO_PAYMENT_REASON_COLOR: Int = 0xFFB05C
