package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.api.event.CommittedMatchEventBatch
import com.doublemoon1119.mahjongcraft.flow.common.error.ExtensionFailurePolicy
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanRoundOutcomeIds
import com.doublemoon1119.mahjongcraft.flow.common.game.model.taiwan.TaiwanWinSettlementIds
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleDecisionResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileValues
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanGameAction
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.BuiltInGameActionIds
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.character.AiCharacterDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AiCharacterRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AiStrategyDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.AutomaticControlDisplayRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.GameAchievementResolverRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.MinecraftTileAssetRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleCatalogueRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.RuleModuleDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileDisplayNameRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileEmojiRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.api.registration.TileLabelRegistrar
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.AiCharacterRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.character.BuiltInAiCharacters
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.RuleDecisionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryDiscardMarkerDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.history.HistoryTileSetAsideDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.RiichiTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.BundledTaiwanMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.TaiwanTranslationKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.TaiwanWinSettlementTemplates
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.RoundOutcomeDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.RuleWordingRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證第三方 Minecraft extension 的統一註冊順序、錯誤診斷與 registry freeze。 */
class MinecraftMahjongExtensionBootstrapTest {
    /** 新角色回呼與其他登記共用來源報告與凍結；重複 ID 以 extension 身分回報。 */
    @Test
    fun `character registration uses the shared bootstrap diagnostics and freeze`() {
        val characters = AiCharacterRegistryImpl()
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:characters"

            /** 在內建角色之後登記第三方描述，不建立原生物件。 */
            override fun registerAiCharacters(registry: AiCharacterRegistrar) {
                assertTrue(BuiltInAiCharacters.TILE_MASCOT_ID in registry.registrationKeys)
                assertFalse(registry.isFrozen)
                registry.register(AiCharacterDefinition("example:mascot", "example.mascot", null, null, null))
            }
        }
        val result = registerAndFreeze(
            extensions = listOf(extension),
            tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
            aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
            tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
            ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
            tileEmojiRegistry = TileEmojiRegistryImpl(),
            tileLabelRegistry = TileLabelRegistryImpl(),
            characterRegistry = characters,
        )
        assertTrue(characters.isFrozen)
        val source = result.sources.single { it.extensionId == extension.id }
        assertEquals(listOf("example:mascot"), source.categories.single { it.id == MinecraftExtensionRegistrationCategoryIds.AI_CHARACTER }.registrationIds)
    }

    /**
     * 以明確的獨立 registry graph 執行啟動流程，供隔離的單元測試使用。
     *
     * @param extensions 要依序執行註冊的 extension。
     * @param tileAssetRegistry 麻將牌資產 registry。
     * @param aiStrategyDisplayNameRegistry AI 策略名稱 registry。
     * @param tileDisplayNameRegistry 牌面顯示名稱 registry。
     * @param ruleModuleDisplayNameRegistry 規則模組名稱 registry。
     * @param tileEmojiRegistry 牌面 emoji registry。
     * @param tileLabelRegistry 牌面輔助標籤 registry。
     * @param automaticControlDisplayRegistry 自動控制顯示 registry。
     * @param ruleCatalogueRegistry 規則目錄 registry。
     * @param gameAchievementResolverRegistry 成果判定 registry。
     * @param characterRegistry 角色描述登記表。
     * @param gameActionVocabularyRegistry 遊戲動作用語 registry。
     * @param ruleDecisionDisplayNameRegistry 規則選擇名稱 registry。
     * @param roundOutcomeDisplayNameRegistry 本局結果名稱 registry。
     * @param winSettlementTemplateRegistry 胡牌結算模板 registry。
     * @return 註冊來源與分類 key 的結果快照。
     */
    private fun registerAndFreeze(
        extensions: Iterable<MinecraftMahjongExtension>,
        tileAssetRegistry: MinecraftTileAssetRegistry,
        aiStrategyDisplayNameRegistry: AiStrategyDisplayNameRegistry,
        tileDisplayNameRegistry: TileDisplayNameRegistry,
        ruleModuleDisplayNameRegistry: RuleModuleDisplayNameRegistry,
        tileEmojiRegistry: TileEmojiRegistry,
        tileLabelRegistry: TileLabelRegistry,
        automaticControlDisplayRegistry: AutomaticControlDisplayRegistry = AutomaticControlDisplayRegistryImpl(),
        ruleCatalogueRegistry: RuleCatalogueRegistryImpl = RuleCatalogueRegistryImpl(),
        gameAchievementResolverRegistry: GameAchievementResolverRegistryImpl = GameAchievementResolverRegistryImpl(),
        characterRegistry: AiCharacterRegistry = AiCharacterRegistryImpl(),
        gameActionVocabularyRegistry: GameActionVocabularyRegistry = GameActionVocabularyRegistryImpl(),
        ruleDecisionDisplayNameRegistry: RuleDecisionDisplayNameRegistry = RuleDecisionDisplayNameRegistryImpl(),
        roundOutcomeDisplayNameRegistry: RoundOutcomeDisplayNameRegistry = RoundOutcomeDisplayNameRegistryImpl(),
        winSettlementTemplateRegistry: WinSettlementPresentationTemplateRegistry = WinSettlementPresentationTemplateRegistryImpl(),
    ): MinecraftMahjongExtensionRegistrationResult = MinecraftMahjongExtensionBootstrap.registerAndFreeze(
        extensions = extensions,
        registries = MinecraftPresentationRegistries(
            tileAssetRegistry = tileAssetRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
            ruleCatalogueRegistry = ruleCatalogueRegistry,
            gameActionVocabularyRegistry = gameActionVocabularyRegistry,
            automaticControlDisplayRegistry = automaticControlDisplayRegistry,
            decisionStatusDisplayNameRegistry = DecisionStatusDisplayNameRegistryImpl(),
            gameActionSoundPresentationRegistry = GameActionSoundPresentationRegistryImpl(),
            exhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl(),
            roundOutcomeDisplayNameRegistry = roundOutcomeDisplayNameRegistry,
            ruleWordingRegistry = RuleWordingRegistryImpl(),
            historyDiscardMarkerDisplayRegistry = HistoryDiscardMarkerDisplayRegistryImpl(),
            historyTileSetAsideDisplayRegistry = HistoryTileSetAsideDisplayRegistryImpl(),
            ruleDecisionDisplayNameRegistry = ruleDecisionDisplayNameRegistry,
            roundInfoLineDisplayRegistry = RoundInfoLineDisplayRegistryImpl(),
            tablePropDescriberRegistry = TablePropDescriberRegistryImpl(),
            winCelebrationShowcaseRegistry = WinCelebrationShowcaseRegistryImpl(),
            winSettlementTemplateRegistry = winSettlementTemplateRegistry,
            matchSettlementTemplateRegistry = MatchSettlementPresentationTemplateRegistryImpl(),
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            aiCharacterRegistry = characterRegistry,
            playerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(failurePolicy = ExtensionFailurePolicy.Default),
            publicPlayerIndicatorDisplayRegistry = PublicPlayerIndicatorDisplayRegistryImpl(),
            roomMemberAppearanceSourceRegistry = RoomMemberAppearanceSourceRegistryImpl(failurePolicy = ExtensionFailurePolicy.Default),
            gameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(
                RuleDecisionResolverRegistry(),
            ),
            gameAchievementResolverRegistry = gameAchievementResolverRegistry,
        ),
        failurePolicy = ExtensionFailurePolicy.Default,
    )

    /** 驗證內建映射先完成註冊，第三方映射接續登記，完成後禁止新增映射。 */
    @Test
    fun `extension registers tile assets and ai strategy names after built-in mappings and before freeze`() {
        val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
        val aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl()
        val tileDisplayNameRegistry = TileDisplayNameRegistryImpl()
        val ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl()
        val tileEmojiRegistry = TileEmojiRegistryImpl()
        val tileLabelRegistry = TileLabelRegistryImpl()
        val automaticControlDisplayRegistry = AutomaticControlDisplayRegistryImpl()
        val thirdPartyId = TileTypeId.parse("example:animal/cat")
        val exampleLabel = TileLabel(topLeft = null, topRight = TileLabelText("C", TileLabelColor.RED))
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:recording"

            override fun registerTileAssets(registry: MinecraftTileAssetRegistrar) {
                registry.register(MinecraftTileAssetDefinition(thirdPartyId, "animal_cat"))
            }

            override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistrar) {
                registry.register(AiStrategyDisplayNameDefinition("example:aggressive", "example.ai_strategy.aggressive"))
            }

            override fun registerAutomaticControlDisplays(registry: AutomaticControlDisplayRegistrar) {
                registry.register(
                    definition = AutomaticControlDisplayDefinition(
                        controlId = "example:auto_flower",
                        display = AutomaticControlDisplay("example.auto_flower", "example.auto_flower.description", 40),
                    ),
                )
            }

            override fun registerTileDisplayNames(registry: TileDisplayNameRegistrar) {
                registry.register(TileDisplayNameDefinition(thirdPartyId, "example.tile.cat"))
            }

            override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistrar) {
                registry.register(RuleModuleDisplayNameDefinition("example:my_rule", "example.rule_module.my_rule"))
            }

            override fun registerTileEmojis(registry: TileEmojiRegistrar) {
                registry.register(TileEmojiDefinition("animal_cat", "🐱"))
            }

            override fun registerTileLabels(registry: TileLabelRegistrar) {
                registry.register(TileLabelDefinition("animal_cat", exampleLabel))
            }

            /** 登記第三方目錄來源，驗證共用 bootstrap 可發現新 hook。 */
            override fun registerRuleCatalogues(registry: RuleCatalogueRegistrar) {
                registry.register(object : RuleCatalogueProvider {
                    /** 第三方規則識別碼。 */
                    override val ruleModuleId: String = "example:my_rule"

                    /** 一般說明配置。 */
                    override fun defaultRuleConfig(): MahjongRuleConfig = RiichiRuleConfig()

                    /** 空的說明目錄仍是有效來源，不執行權威規則判定。 */
                    override fun catalogue(config: MahjongRuleConfig): RuleCatalogue = RuleCatalogue(emptyList(), emptyList())
                })
            }
        }

        val result = registerAndFreeze(
            extensions = BundledMinecraftMahjongExtensions.all + extension,
            tileAssetRegistry = tileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
            automaticControlDisplayRegistry = automaticControlDisplayRegistry,
        )

        assertEquals("m5_red", tileAssetRegistry.findStyle(RiichiTileValues.RED_FIVE_APPEARANCE_ID, Tile.Numeric(Tile.Suit.Character, 5)))
        assertEquals("animal_cat", tileAssetRegistry.find(thirdPartyId))
        assertEquals(setOf("animal_cat"), result.registrationKeys("mahjongcraft:tile_asset"))
        assertTrue(tileAssetRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileAssetRegistry.register(MinecraftTileAssetDefinition(TileTypeId.parse("example:late"), "late_key"))
        }

        assertEquals(
            "example.ai_strategy.aggressive",
            aiStrategyDisplayNameRegistry.find("example:aggressive"),
        )
        assertEquals(setOf("example:aggressive"), result.registrationKeys("mahjongcraft:ai_strategy_display_name"))
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            aiStrategyDisplayNameRegistry.register(AiStrategyDisplayNameDefinition("example:late", "example.ai_strategy.late"))
        }

        assertEquals("example.auto_flower", automaticControlDisplayRegistry.find(null, "example:auto_flower")?.labelTranslationKey)
        assertEquals(
            setOf("example:auto_flower"),
            result.registrationKeys("mahjongcraft:automatic_control_display"),
        )
        assertTrue(automaticControlDisplayRegistry.isFrozen)

        assertTrue(tileDisplayNameRegistry.findStyle(RiichiTileValues.RED_FIVE_APPEARANCE_ID, Tile.Numeric(Tile.Suit.Character, 5)) != null)
        assertEquals("example.tile.cat", tileDisplayNameRegistry.find(thirdPartyId))
        assertEquals(setOf(thirdPartyId.toString()), result.registrationKeys("mahjongcraft:tile_display_name"))
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileDisplayNameRegistry.register(TileDisplayNameDefinition(TileTypeId.parse("example:late"), "example.tile.late"))
        }

        assertTrue(ruleModuleDisplayNameRegistry.find("mahjongcraft:riichi") != null)
        assertEquals(
            "example.rule_module.my_rule",
            ruleModuleDisplayNameRegistry.find("example:my_rule"),
        )
        assertEquals(setOf("example:my_rule"), result.registrationKeys("mahjongcraft:rule_module_display_name"))
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            ruleModuleDisplayNameRegistry.register(RuleModuleDisplayNameDefinition("example:late", "example.rule_module.late"))
        }

        assertTrue(tileEmojiRegistry.find("m1") != null)
        assertEquals("🐱", tileEmojiRegistry.find("animal_cat"))
        assertEquals(setOf("animal_cat"), result.registrationKeys("mahjongcraft:tile_emoji"))
        assertTrue(tileEmojiRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileEmojiRegistry.register(TileEmojiDefinition("late_key", "🐶"))
        }

        assertTrue(tileLabelRegistry.find("m9") != null)
        assertEquals(exampleLabel, tileLabelRegistry.find("animal_cat"))
        assertEquals(setOf("animal_cat"), result.registrationKeys("mahjongcraft:tile_label"))
        assertTrue(tileLabelRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileLabelRegistry.register(TileLabelDefinition("late_key", exampleLabel))
        }

        assertEquals(setOf("example:my_rule"), result.registrationKeys("mahjongcraft:rule_catalogue"))
    }

    /** 只帶內建規則 extension 時，日麻與台麻的映射都完成登記並凍結。 */
    @Test
    fun `bundled extensions register built-in rule presentations and freeze`() {
        val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
        val aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl()
        val tileDisplayNameRegistry = TileDisplayNameRegistryImpl()
        val ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl()
        val tileEmojiRegistry = TileEmojiRegistryImpl()
        val tileLabelRegistry = TileLabelRegistryImpl()
        val ruleCatalogueRegistry = RuleCatalogueRegistryImpl()
        val gameActionVocabularyRegistry = GameActionVocabularyRegistryImpl()
        val ruleDecisionDisplayNameRegistry = RuleDecisionDisplayNameRegistryImpl()
        val roundOutcomeDisplayNameRegistry = RoundOutcomeDisplayNameRegistryImpl()
        val winSettlementTemplateRegistry = WinSettlementPresentationTemplateRegistryImpl()

        val result = registerAndFreeze(
            extensions = BundledMinecraftMahjongExtensions.all,
            tileAssetRegistry = tileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
            ruleCatalogueRegistry = ruleCatalogueRegistry,
            gameActionVocabularyRegistry = gameActionVocabularyRegistry,
            ruleDecisionDisplayNameRegistry = ruleDecisionDisplayNameRegistry,
            roundOutcomeDisplayNameRegistry = roundOutcomeDisplayNameRegistry,
            winSettlementTemplateRegistry = winSettlementTemplateRegistry,
        )

        assertEquals("flower_spring", tileAssetRegistry.find(TaiwanTileTypes.SPRING))
        assertTrue(tileAssetRegistry.isFrozen)
        assertNull(tileAssetRegistry.find(TileTypeId.parse("example:unregistered")))

        assertTrue(aiStrategyDisplayNameRegistry.find(RandomAiStrategy.KEY) != null)
        assertEquals(MinecraftMessageKeys.AI_STRATEGY_BEGINNER, aiStrategyDisplayNameRegistry.find(BuiltInAiStrategyKeys.BEGINNER))
        assertEquals(MinecraftMessageKeys.AI_STRATEGY_INTERMEDIATE, aiStrategyDisplayNameRegistry.find(BuiltInAiStrategyKeys.INTERMEDIATE))
        assertEquals(MinecraftMessageKeys.AI_STRATEGY_ADVANCED, aiStrategyDisplayNameRegistry.find(BuiltInAiStrategyKeys.ADVANCED))
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertNull(aiStrategyDisplayNameRegistry.find("example:unregistered"))

        assertTrue(tileDisplayNameRegistry.findStyle(RiichiTileValues.RED_FIVE_APPEARANCE_ID, Tile.Numeric(Tile.Suit.Dot, 5)) != null)
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertNull(tileDisplayNameRegistry.find(TileTypeId.parse("example:unregistered")))

        assertTrue(ruleModuleDisplayNameRegistry.find("mahjongcraft:taiwan") != null)
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertNull(ruleModuleDisplayNameRegistry.find("example:unregistered"))

        assertTrue(tileEmojiRegistry.find("unknown") != null)
        assertTrue(tileEmojiRegistry.isFrozen)
        assertNull(tileEmojiRegistry.find("example_unregistered"))

        assertTrue(tileLabelRegistry.find("flower_spring") != null)
        assertTrue(tileLabelRegistry.isFrozen)
        assertNull(tileLabelRegistry.find("unknown"))
        assertNull(tileLabelRegistry.find("example_unregistered"))
        assertTrue(ruleCatalogueRegistry.isFrozen)
        assertEquals(
            setOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, BuiltInRuleModuleIds.TAIWAN),
            ruleCatalogueRegistry.registrationKeys,
        )
        assertEquals(
            TaiwanTranslationKeys.ACTION_REVEAL_FLOWER,
            gameActionVocabularyRegistry.find(BuiltInRuleModuleIds.TAIWAN, TaiwanGameAction.RevealFlower.id)?.labelKey,
        )
        // 共用預設是中立用語；台灣麻將把和說成胡牌，日本麻將登記榮和與自己的吃碰槓說法，三人日麻相同。
        assertEquals(TaiwanTranslationKeys.ACTION_HU_MESSAGE, gameActionVocabularyRegistry.find(BuiltInRuleModuleIds.TAIWAN, BuiltInGameActionIds.RON)?.messageKey)
        assertEquals(MinecraftMessageKeys.GAME_ACTION_CHI, gameActionVocabularyRegistry.find(BuiltInRuleModuleIds.TAIWAN, BuiltInGameActionIds.CHI)?.messageKey)
        assertEquals(RiichiTranslationKeys.ACTION_RON_MESSAGE, gameActionVocabularyRegistry.find(BuiltInRuleModuleIds.RIICHI, BuiltInGameActionIds.RON)?.messageKey)
        assertEquals(RiichiTranslationKeys.ACTION_KAN, gameActionVocabularyRegistry.find(BuiltInRuleModuleIds.RIICHI_THREE_PLAYER, BuiltInGameActionIds.KAN_CLOSED)?.labelKey)
        assertEquals(MinecraftMessageKeys.GAME_ACTION_RON, gameActionVocabularyRegistry.find("example:rule", BuiltInGameActionIds.RON)?.messageKey)
        assertTrue(ruleDecisionDisplayNameRegistry.registrationKeys.isEmpty(), "Built-in rules show rule decision choices with action names.")
        assertTrue(TaiwanRoundOutcomeIds.EIGHT_FLOWERS.let(roundOutcomeDisplayNameRegistry::find) != null)
        assertTrue(TaiwanRoundOutcomeIds.SEVEN_FLOWERS.let(roundOutcomeDisplayNameRegistry::find) != null)
        assertEquals(
            TaiwanWinSettlementTemplates.TEMPLATE_KEY,
            winSettlementTemplateRegistry.findTemplateForRule(BuiltInRuleModuleIds.TAIWAN)?.key,
        )
        assertTrue(winSettlementTemplateRegistry.findDetailTextFormatter(TaiwanWinSettlementIds.TAI_FIELD) != null)
        assertEquals(listOf(null, BundledRiichiMinecraftExtension.id, BundledTaiwanMinecraftExtension.id), result.sources.map { it.extensionId })
        assertEquals(
            setOf(BuiltInRuleModuleIds.RIICHI, BuiltInRuleModuleIds.RIICHI_THREE_PLAYER),
            result.sources[1].categories.single { it.id == "mahjongcraft:rule_catalogue" }.registrationIds.toSet(),
        )
    }

    /** 不帶任何 extension 時只登記規則中立的映射，日麻與台麻的映射都不存在。 */
    @Test
    fun `bootstrap alone registers only rule-neutral mappings`() {
        val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
        val aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl()
        val ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl()
        val tileEmojiRegistry = TileEmojiRegistryImpl()
        val tileLabelRegistry = TileLabelRegistryImpl()

        val result = registerAndFreeze(
            extensions = emptyList(),
            tileAssetRegistry = tileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
        )

        assertEquals(listOf(null), result.sources.map { it.extensionId })
        assertEquals(MinecraftMessageKeys.AI_STRATEGY_BEGINNER, aiStrategyDisplayNameRegistry.find(BuiltInAiStrategyKeys.BEGINNER))
        assertTrue(tileEmojiRegistry.find("m1") != null)
        assertTrue(tileLabelRegistry.find("east") != null)
        assertNull(tileEmojiRegistry.find("m5_red"))
        assertNull(tileLabelRegistry.find("flower_spring"))
        assertNull(tileAssetRegistry.findStyle(RiichiTileValues.RED_FIVE_APPEARANCE_ID, Tile.Numeric(Tile.Suit.Character, 5)))
        assertNull(ruleModuleDisplayNameRegistry.find(BuiltInRuleModuleIds.RIICHI))
    }

    /** 驗證註冊失敗時的例外會指出第三方 extension ID。 */
    @Test
    fun `registration failure identifies extension`() {
        val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:broken"

            override fun registerTileAssets(registry: MinecraftTileAssetRegistrar) {
                error("broken")
            }
        }

        val error = assertFailsWith<MinecraftMahjongExtensionRegistrationException> {
            registerAndFreeze(
                extensions = listOf(extension),
                tileAssetRegistry = tileAssetRegistry,
                aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
                tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
                ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
                tileEmojiRegistry = TileEmojiRegistryImpl(),
                tileLabelRegistry = TileLabelRegistryImpl(),
            )
        }

        assertTrue(error.message.orEmpty().contains("example:broken"))
        assertFalse(tileAssetRegistry.isFrozen)
    }

    /** 驗證第三方規則的成果判定會登記到共用 registry，出現在診斷分類中，完成後凍結。 */
    @Test
    fun `extension registers game achievement resolvers before freeze`() {
        val gameAchievementResolverRegistry = GameAchievementResolverRegistryImpl()
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:achievements"

            override fun registerGameAchievementResolvers(registry: GameAchievementResolverRegistrar) {
                registry.register(object : GameAchievementResolver {
                    /** 第三方規則識別碼。 */
                    override val ruleModuleId: String = "example:my_rule"

                    /** 測試用判定不產生成果。 */
                    override fun resolve(batch: CommittedMatchEventBatch): Map<Uuid, Set<String>> = emptyMap()
                })
            }
        }

        val result = registerAndFreeze(
            extensions = listOf(extension),
            tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
            aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
            tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
            ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
            tileEmojiRegistry = TileEmojiRegistryImpl(),
            tileLabelRegistry = TileLabelRegistryImpl(),
            gameAchievementResolverRegistry = gameAchievementResolverRegistry,
        )

        assertEquals(setOf("example:my_rule"), result.registrationKeys("mahjongcraft:game_achievement_resolver"))
        assertTrue(gameAchievementResolverRegistry.isFrozen)
    }

    /** 驗證相同 extension ID 不會形成無法判斷來源的部分註冊結果。 */
    @Test
    fun `duplicate extension id fails registration`() {
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:duplicate"
        }

        val error = assertFailsWith<MinecraftMahjongExtensionRegistrationException> {
            registerAndFreeze(
                extensions = listOf(extension, extension),
                tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
                aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
                tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
                ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
                tileEmojiRegistry = TileEmojiRegistryImpl(),
                tileLabelRegistry = TileLabelRegistryImpl(),
            )
        }

        assertTrue(error.message.orEmpty().contains(extension.id))
        assertTrue(error.cause?.message.orEmpty().contains("Duplicate"))
    }

    /** 驗證不符合 namespace:path 格式的 extension ID 會在凍結前被拒絕。 */
    @Test
    fun `invalid extension id fails registration before freeze`() {
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "invalid-id"
        }

        val error = assertFailsWith<MinecraftMahjongExtensionRegistrationException> {
            registerAndFreeze(
                extensions = listOf(extension),
                tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
                aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
                tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
                ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
                tileEmojiRegistry = TileEmojiRegistryImpl(),
                tileLabelRegistry = TileLabelRegistryImpl(),
            )
        }

        assertTrue(error.message.orEmpty().contains(extension.id))
        assertTrue(error.cause?.message.orEmpty().contains("Invalid"))
    }

    /** 驗證沒有新增映射的 extension 仍保留來源但不產生空分類。 */
    @Test
    fun `no-op extension produces an empty source`() {
        val result = registerAndFreeze(
            extensions = listOf(object : MinecraftMahjongExtension {
                override val id: String = "example:noop"
            }),
            tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
            aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
            tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
            ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
            tileEmojiRegistry = TileEmojiRegistryImpl(),
            tileLabelRegistry = TileLabelRegistryImpl(),
        )

        assertEquals("example:noop", result.sources.last().extensionId)
        assertTrue(result.sources.last().categories.isEmpty())
    }

    /** 驗證保留的內建 ID 會形成具來源內容的重複 ID 錯誤。 */
    @Test
    fun `reserved built-in id fails with contextual duplicate error`() {
        val extension = object : MinecraftMahjongExtension {
            override val id: String = BuiltInMinecraftMahjongExtension.id
        }

        val error = assertFailsWith<MinecraftMahjongExtensionRegistrationException> {
            registerAndFreeze(
                extensions = listOf(extension),
                tileAssetRegistry = MinecraftTileAssetRegistryImpl(),
                aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl(),
                tileDisplayNameRegistry = TileDisplayNameRegistryImpl(),
                ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl(),
                tileEmojiRegistry = TileEmojiRegistryImpl(),
                tileLabelRegistry = TileLabelRegistryImpl(),
            )
        }

        assertTrue(error.message.orEmpty().contains(BuiltInMinecraftMahjongExtension.id))
        assertTrue(error.cause?.message.orEmpty().contains("Duplicate"))
    }
}

/**
 * 取得第三方 extension（不含內建映射與內建規則 extension）在指定診斷分類登記的 key。
 *
 * @param categoryId 要查詢的診斷分類 ID。
 * @return 該分類中由第三方 extension 新增的登記 key。
 */
private fun MinecraftMahjongExtensionRegistrationResult.registrationKeys(categoryId: String): Set<String> = sources
    .filter { source -> source.extensionId != null && BundledMinecraftMahjongExtensions.all.none { it.id == source.extensionId } }
    .flatMap { it.categories }
    .filter { it.id == categoryId }
    .flatMapTo(mutableSetOf()) { it.registrationIds }
