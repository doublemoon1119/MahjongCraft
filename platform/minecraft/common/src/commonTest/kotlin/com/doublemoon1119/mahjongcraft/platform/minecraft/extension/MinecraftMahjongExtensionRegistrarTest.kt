package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.ai.BuiltInAiStrategyKeys
import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.flow.common.game.history.CommittedGameFacts
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolver
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.achievement.GameAchievementResolverRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionVocabularyRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplay
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.automatic.AutomaticControlDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogue
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueProvider
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.decision.DecisionStatusDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.BundledRiichiMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.BundledTaiwanMinecraftExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.TablePropDescriberRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.text.MinecraftMessageKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileEmojiRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabel
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelColor
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileLabelText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證第三方 Minecraft extension 的統一註冊順序、錯誤診斷與 registry freeze。 */
class MinecraftMahjongExtensionRegistrarTest {
    /** 以明確的獨立 registry graph 執行 registrar，供隔離的單元測試使用。 */
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
    ): MinecraftMahjongExtensionRegistrationResult = MinecraftMahjongExtensionRegistrar.registerAndFreeze(
        extensions = extensions,
        registries = MinecraftPresentationRegistries(
            tileAssetRegistry = tileAssetRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
            ruleCatalogueRegistry = ruleCatalogueRegistry,
            gameActionVocabularyRegistry = GameActionVocabularyRegistryImpl(),
            automaticControlDisplayRegistry = automaticControlDisplayRegistry,
            decisionStatusDisplayNameRegistry = DecisionStatusDisplayNameRegistryImpl(),
            gameActionSoundPresentationRegistry = GameActionSoundPresentationRegistryImpl(),
            exhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl(),
            roundPreparationDisplayNameRegistry = RoundPreparationDisplayNameRegistryImpl(),
            roundInfoLineDisplayRegistry = RoundInfoLineDisplayRegistryImpl(),
            tablePropDescriberRegistry = TablePropDescriberRegistryImpl(),
            winCelebrationShowcaseRegistry = WinCelebrationShowcaseRegistryImpl(),
            winSettlementTemplateRegistry = WinSettlementPresentationTemplateRegistryImpl(),
            matchSettlementTemplateRegistry = MatchSettlementPresentationTemplateRegistryImpl(),
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            playerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(),
            publicPlayerIndicatorDisplayRegistry = PublicPlayerIndicatorDisplayRegistryImpl(),
            roomMemberAppearanceSourceRegistry = RoomMemberAppearanceSourceRegistryImpl(),
            gameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(),
            gameAchievementResolverRegistry = gameAchievementResolverRegistry,
        ),
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

            override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
                registry.register(thirdPartyId, "animal_cat")
            }

            override fun registerAiStrategyDisplayNames(registry: AiStrategyDisplayNameRegistry) {
                registry.register("example:aggressive", "example.ai_strategy.aggressive")
            }

            override fun registerAutomaticControlDisplays(registry: AutomaticControlDisplayRegistry) {
                registry.register(
                    "example:auto_flower",
                    AutomaticControlDisplay("example.auto_flower", "example.auto_flower.description", 40),
                )
            }

            override fun registerTileDisplayNames(registry: TileDisplayNameRegistry) {
                registry.register(thirdPartyId, "example.tile.cat")
            }

            override fun registerRuleModuleDisplayNames(registry: RuleModuleDisplayNameRegistry) {
                registry.register("example:my_rule", "example.rule_module.my_rule")
            }

            override fun registerTileEmojis(registry: TileEmojiRegistry) {
                registry.register("animal_cat", "🐱")
            }

            override fun registerTileLabels(registry: TileLabelRegistry) {
                registry.register("animal_cat", exampleLabel)
            }

            /** 登記第三方目錄來源，驗證共用 bootstrap 可發現新 hook。 */
            override fun registerRuleCatalogues(registry: RuleCatalogueRegistry) {
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

        assertEquals("m5_red", tileAssetRegistry.find(RiichiTileTypes.RED_FIVE_CHARACTER))
        assertEquals("animal_cat", tileAssetRegistry.find(thirdPartyId))
        assertEquals(setOf("animal_cat"), result.registrationKeys("mahjongcraft:tile_asset"))
        assertTrue(tileAssetRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileAssetRegistry.register(TileTypeId.parse("example:late"), "late_key")
        }

        assertEquals(
            "example.ai_strategy.aggressive",
            aiStrategyDisplayNameRegistry.find("example:aggressive"),
        )
        assertEquals(setOf("example:aggressive"), result.registrationKeys("mahjongcraft:ai_strategy_display_name"))
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            aiStrategyDisplayNameRegistry.register("example:late", "example.ai_strategy.late")
        }

        assertEquals("example.auto_flower", automaticControlDisplayRegistry.find("example:auto_flower")?.labelTranslationKey)
        assertEquals(
            setOf("example:auto_flower"),
            result.registrationKeys("mahjongcraft:automatic_control_display"),
        )
        assertTrue(automaticControlDisplayRegistry.isFrozen)

        assertTrue(tileDisplayNameRegistry.find(RiichiTileTypes.RED_FIVE_CHARACTER) != null)
        assertEquals("example.tile.cat", tileDisplayNameRegistry.find(thirdPartyId))
        assertEquals(setOf(thirdPartyId.toString()), result.registrationKeys("mahjongcraft:tile_display_name"))
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileDisplayNameRegistry.register(TileTypeId.parse("example:late"), "example.tile.late")
        }

        assertTrue(ruleModuleDisplayNameRegistry.find("mahjongcraft:riichi") != null)
        assertEquals(
            "example.rule_module.my_rule",
            ruleModuleDisplayNameRegistry.find("example:my_rule"),
        )
        assertEquals(setOf("example:my_rule"), result.registrationKeys("mahjongcraft:rule_module_display_name"))
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            ruleModuleDisplayNameRegistry.register("example:late", "example.rule_module.late")
        }

        assertTrue(tileEmojiRegistry.find("m1") != null)
        assertEquals("🐱", tileEmojiRegistry.find("animal_cat"))
        assertEquals(setOf("animal_cat"), result.registrationKeys("mahjongcraft:tile_emoji"))
        assertTrue(tileEmojiRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileEmojiRegistry.register("late_key", "🐶")
        }

        assertTrue(tileLabelRegistry.find("m9") != null)
        assertEquals(exampleLabel, tileLabelRegistry.find("animal_cat"))
        assertEquals(setOf("animal_cat"), result.registrationKeys("mahjongcraft:tile_label"))
        assertTrue(tileLabelRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileLabelRegistry.register("late_key", exampleLabel)
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

        val result = registerAndFreeze(
            extensions = BundledMinecraftMahjongExtensions.all,
            tileAssetRegistry = tileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
            ruleCatalogueRegistry = ruleCatalogueRegistry,
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

        assertTrue(tileDisplayNameRegistry.find(RiichiTileTypes.RED_FIVE_DOT) != null)
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
        assertEquals(setOf(BuiltInRuleModuleIds.RIICHI), ruleCatalogueRegistry.registrationKeys)
        assertEquals(listOf(null, BundledRiichiMinecraftExtension.id, BundledTaiwanMinecraftExtension.id), result.sources.map { it.extensionId })
        assertEquals(
            setOf(BuiltInRuleModuleIds.RIICHI),
            result.sources[1].categories.single { it.id == "mahjongcraft:rule_catalogue" }.registrationKeys,
        )
    }

    /** 不帶任何 extension 時只登記規則中立的映射，日麻與台麻的映射都不存在。 */
    @Test
    fun `registrar alone registers only rule-neutral mappings`() {
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
        assertNull(tileAssetRegistry.find(RiichiTileTypes.RED_FIVE_CHARACTER))
        assertNull(ruleModuleDisplayNameRegistry.find(BuiltInRuleModuleIds.RIICHI))
    }

    /** 驗證註冊失敗時的例外會指出第三方 extension ID。 */
    @Test
    fun `registration failure identifies extension`() {
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:broken"

            override fun registerTileAssets(registry: MinecraftTileAssetRegistry) {
                error("broken")
            }
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

        assertTrue(error.message.orEmpty().contains("example:broken"))
    }

    /** 驗證第三方規則的成果判定會登記到共用 registry，出現在診斷分類中，完成後凍結。 */
    @Test
    fun `extension registers game achievement resolvers before freeze`() {
        val gameAchievementResolverRegistry = GameAchievementResolverRegistryImpl()
        val extension = object : MinecraftMahjongExtension {
            override val id: String = "example:achievements"

            override fun registerGameAchievementResolvers(registry: GameAchievementResolverRegistry) {
                registry.register(object : GameAchievementResolver {
                    /** 第三方規則識別碼。 */
                    override val ruleModuleId: String = "example:my_rule"

                    /** 測試用判定不產生成果。 */
                    override fun resolve(facts: CommittedGameFacts): Map<Uuid, Set<String>> = emptyMap()
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
}

/** 取得第三方 extension（不含內建映射與內建規則 extension）在指定診斷分類登記的 key。 */
private fun MinecraftMahjongExtensionRegistrationResult.registrationKeys(categoryId: String): Set<String> = sources
    .filter { source -> source.extensionId != null && BundledMinecraftMahjongExtensions.all.none { it.id == source.extensionId } }
    .flatMap { it.categories }
    .filter { it.id == categoryId }
    .flatMapTo(mutableSetOf()) { it.registrationKeys }
