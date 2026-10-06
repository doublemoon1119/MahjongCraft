package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInAutomaticControlIds
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInPaymentReasonIds
import com.doublemoon1119.mahjongcraft.metadata.MahjongCraftMetadata
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabulary
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.HUD_ACTION_PREFIX
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.MinecraftKanActionTokenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.BuiltInMinecraftAutomaticControlIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.config.MinecraftClientConfigScreenKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.BuiltInDecisionStatusIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.HUD_STATUS_PREFIX
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementFieldIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.BuiltInWinSettlementTemplates
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplate
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.PresentationValue
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementTextKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
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

    override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistry) {
        registry.register(RandomAiStrategy.KEY, MinecraftMessageKeys.AI_STRATEGY_RANDOM)
        registry.register(BuiltInAiStrategyKeys.BEGINNER, MinecraftMessageKeys.AI_STRATEGY_BEGINNER)
        registry.register(BuiltInAiStrategyKeys.INTERMEDIATE, MinecraftMessageKeys.AI_STRATEGY_INTERMEDIATE)
        registry.register(BuiltInAiStrategyKeys.ADVANCED, MinecraftMessageKeys.AI_STRATEGY_ADVANCED)
    }

    override fun registerAutomaticControlDisplays(registry: AutomaticControlDisplayRegistry) {
        registry.register(
            BuiltInMinecraftAutomaticControlIds.AUTO_SORT_HAND,
            AutomaticControlDisplay(
                MinecraftClientConfigScreenKeys.AUTO_SORT_HAND,
                MinecraftClientConfigScreenKeys.AUTO_SORT_HAND_DESCRIPTION,
                displayOrder = 0,
            ),
        )
        registry.register(BuiltInAutomaticControlIds.AUTO_WIN, roundControlDisplay("auto_win", displayOrder = 10))
        registry.register(BuiltInAutomaticControlIds.DECLINE_CALLS, roundControlDisplay("decline_calls", displayOrder = 20))
        registry.register(BuiltInAutomaticControlIds.AUTO_TSUMOGIRI, roundControlDisplay("auto_tsumogiri", displayOrder = 30))
    }

    /**
     * 34 種基本牌與未知牌的 asset key 對應的 Unicode「Mahjong Tiles」區塊字元（U+1F000~U+1F02F）。這組字元由
     * `assets/minecraft/font/default.json`（見 `platform/minecraft/common/src/jvmMain/resources`）註冊成自訂 bitmap 字型，
     * 讓文字內容直接顯示成牌面貼圖；規則專屬牌（例如日麻的赤五）由該規則的 extension 登記同一區塊的字元。
     */
    override fun registerTileEmojis(registry: TileEmojiRegistry) {
        registry.register("east", "🀀")
        registry.register("south", "🀁")
        registry.register("west", "🀂")
        registry.register("north", "🀃")
        registry.register("red_dragon", "🀄")
        registry.register("green_dragon", "🀅")
        registry.register("white_dragon", "🀆")
        registry.register("m1", "🀇")
        registry.register("m2", "🀈")
        registry.register("m3", "🀉")
        registry.register("m4", "🀊")
        registry.register("m5", "🀋")
        registry.register("m6", "🀌")
        registry.register("m7", "🀍")
        registry.register("m8", "🀎")
        registry.register("m9", "🀏")
        registry.register("s1", "🀐")
        registry.register("s2", "🀑")
        registry.register("s3", "🀒")
        registry.register("s4", "🀓")
        registry.register("s5", "🀔")
        registry.register("s6", "🀕")
        registry.register("s7", "🀖")
        registry.register("s8", "🀗")
        registry.register("s9", "🀘")
        registry.register("p1", "🀙")
        registry.register("p2", "🀚")
        registry.register("p3", "🀛")
        registry.register("p4", "🀜")
        registry.register("p5", "🀝")
        registry.register("p6", "🀞")
        registry.register("p7", "🀟")
        registry.register("p8", "🀠")
        registry.register("p9", "🀡")
        registry.register(UNKNOWN_TILE_ASSET_KEY, "🀯")
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
        registry.register("m1", redText("1"))
        registry.register("m2", redText("2"))
        registry.register("m3", redText("3"))
        registry.register("m4", redText("4"))
        registry.register("m5", redText("5"))
        registry.register("m6", redText("6"))
        registry.register("m7", redText("7"))
        registry.register("m8", redText("8"))
        registry.register("m9", redText("9"))

        // 條子
        registry.register("s1", redText("1"))
        registry.register("s2", redText("2"))
        registry.register("s3", redText("3"))
        registry.register("s4", redText("4"))
        registry.register("s5", redText("5"))
        registry.register("s6", redText("6"))
        registry.register("s7", redText("7"))
        registry.register("s8", redText("8"))
        registry.register("s9", redText("9"))

        // 餅子
        registry.register("p1", redText("1"))
        registry.register("p2", redText("2"))
        registry.register("p3", redText("3"))
        registry.register("p4", redText("4"))
        registry.register("p5", redText("5"))
        registry.register("p6", redText("6"))
        registry.register("p7", redText("7"))
        registry.register("p8", redText("8"))
        registry.register("p9", redText("9"))

        // 風牌
        registry.register("east", redText("E"))
        registry.register("south", redText("S"))
        registry.register("west", redText("W"))
        registry.register("north", redText("N"))

        // 三元牌：紅中牌面本身印刷成紅色，跟赤牌歸為同一種黑字規則
        registry.register("red_dragon", blackTextOnRedTile("R"))
        registry.register("green_dragon", redText("G"))
        registry.register("white_dragon", redText("Wh"))
    }

    /** 通用 fallback 模板與共用欄位。 */
    override fun registerWinSettlementPresentationTemplates(registry: WinSettlementPresentationTemplateRegistry) {
        registry.registerTemplate(BuiltInWinSettlementTemplates.GENERIC)
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.COMPLETE_HAND) { snapshot ->
            PresentationValue.TileListValue(snapshot.tileAssetKeys)
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.COMPLETE_HAND_GROUPS) { snapshot ->
            PresentationValue.TileGroupsValue(snapshot.tileAssetGroups)
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.OUTCOME_TITLE) { snapshot ->
            PresentationValue.TextValue(if (snapshot.isTsumo) WinSettlementTextKeys.TSUMO else WinSettlementTextKeys.RON)
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.WINNER_SUMMARY) { snapshot ->
            if (snapshot.isTsumo) {
                PresentationValue.TextValue("%s", listOf(snapshot.winnerDisplayName))
            } else {
                PresentationValue.TextValue(
                    WinSettlementTextKeys.RON_RELATIONSHIP,
                    listOf(snapshot.winnerDisplayName, snapshot.responsiblePlayerDisplayName.orEmpty()),
                )
            }
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.WINNER_IDENTITY) { snapshot ->
            PresentationValue.PlayerIdentityValue(snapshot.winnerId, snapshot.winnerDisplayName, snapshot.winnerIsAi)
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.RESPONSIBLE_PLAYER_IDENTITY) { snapshot ->
            snapshot.responsiblePlayerId?.let { id ->
                PresentationValue.PlayerIdentityValue(
                    id,
                    snapshot.responsiblePlayerDisplayName.orEmpty(),
                    snapshot.responsiblePlayerIsAi == true,
                )
            }
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.RELATION_ARROW) { snapshot ->
            snapshot.responsiblePlayerId?.let { PresentationValue.TextValue(WinSettlementTextKeys.RELATIONSHIP_ARROW) }
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.DORA_LABEL) {
            PresentationValue.TextValue(WinSettlementTextKeys.DORA)
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.URA_DORA_LABEL) {
            PresentationValue.TextValue(WinSettlementTextKeys.URA_DORA)
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.PAYMENT_SUMMARY) { null }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.WINNING_TILE) { snapshot ->
            snapshot.winningTileAssetKey?.let(PresentationValue::TileValue)
        }
        registry.registerFieldProvider(BuiltInWinSettlementFieldIds.TOTAL_SCORE) { snapshot ->
            PresentationValue.TextValue(WinSettlementTextKeys.TOTAL_SCORE, listOf(snapshot.totalScore.toString()))
        }
    }

    override fun registerMatchSettlementPresentationTemplates(registry: MatchSettlementPresentationTemplateRegistry) {
        registry.register(
            MatchSettlementPresentationTemplate(
                key = BUILT_IN_MATCH_SETTLEMENT_TEMPLATE_KEY,
                titleTranslationKey = MinecraftMessageKeys.MATCH_SETTLEMENT_TITLE,
            ),
        )
    }

    override fun registerPublicPlayerIndicatorDisplays(registry: PublicPlayerIndicatorDisplayRegistry) {
        registry.register(
            BuiltInPaymentReasonIds.PAO,
            PublicPlayerIndicatorDisplay(MinecraftMessageKeys.PLAYER_INDICATOR_PAO, colorRgb = PAO_PAYMENT_REASON_COLOR),
        )
    }

    /** 核心動作的中立預設用語與操作卡順序；說法不同的規則登記同一個動作 ID 即可覆寫，不需要改動呈現程式。 */
    override fun registerGameActionVocabulary(registry: GameActionVocabularyRegistry) {
        registry.registerDefault(
            BuiltInGameActionIds.CHI,
            GameActionVocabulary(HUD_ACTION_PREFIX + "chi", MinecraftMessageKeys.GAME_ACTION_CHI, order = 0),
        )
        registry.registerDefault(
            BuiltInGameActionIds.PON,
            GameActionVocabulary(HUD_ACTION_PREFIX + "pon", MinecraftMessageKeys.GAME_ACTION_PON, order = 1),
        )
        registry.registerDefault(
            BuiltInGameActionIds.KAN_OPEN,
            GameActionVocabulary(HUD_ACTION_PREFIX + MinecraftKanActionTokenKeys.OPEN, MinecraftMessageKeys.GAME_ACTION_KAN_OPEN, order = 2),
        )
        registry.registerDefault(
            BuiltInGameActionIds.KAN_CLOSED,
            GameActionVocabulary(HUD_ACTION_PREFIX + MinecraftKanActionTokenKeys.CLOSED, MinecraftMessageKeys.GAME_ACTION_KAN_CLOSED, order = 2),
        )
        registry.registerDefault(
            BuiltInGameActionIds.KAN_ADDED,
            GameActionVocabulary(HUD_ACTION_PREFIX + MinecraftKanActionTokenKeys.ADDED, MinecraftMessageKeys.GAME_ACTION_KAN_ADDED, order = 2),
        )
        registry.registerDefault(
            BuiltInGameActionIds.RON,
            GameActionVocabulary(HUD_ACTION_PREFIX + "ron", MinecraftMessageKeys.GAME_ACTION_RON, order = 3),
        )
        registry.registerDefault(
            BuiltInGameActionIds.TSUMO,
            GameActionVocabulary(HUD_ACTION_PREFIX + "tsumo", MinecraftMessageKeys.GAME_ACTION_TSUMO, order = 4),
        )
        registry.registerDefault(
            BuiltInGameActionIds.PASS,
            GameActionVocabulary(HUD_ACTION_PREFIX + "pass", MinecraftMessageKeys.GAME_ACTION_PASS),
        )
        registry.registerDefault(
            BuiltInGameActionIds.DISCARD,
            GameActionVocabulary(HUD_ACTION_PREFIX + "discard", MinecraftMessageKeys.GAME_ACTION_DISCARD),
        )
    }

    /** 中立預設：和牌資格沒有特殊限制。 */
    override fun registerDecisionStatusDisplayNames(registry: DecisionStatusDisplayNameRegistry) {
        registry.registerDefault(BuiltInDecisionStatusIds.WIN_AVAILABLE, HUD_STATUS_PREFIX + "win_availability.available")
    }
}

/** 只在本局有效的內建自動操作的顯示設定；名稱與說明的 translation key 由 [translationId] 組成。 */
private fun roundControlDisplay(translationId: String, displayOrder: Int): AutomaticControlDisplay {
    val prefix = "mahjongcraft.automatic_control.$translationId"
    return AutomaticControlDisplay(prefix, "$prefix.description", displayOrder)
}

/** 包牌付款原因的文字顏色；與分數增減的紅綠色區隔。 */
private const val PAO_PAYMENT_REASON_COLOR: Int = 0xFFB05C
