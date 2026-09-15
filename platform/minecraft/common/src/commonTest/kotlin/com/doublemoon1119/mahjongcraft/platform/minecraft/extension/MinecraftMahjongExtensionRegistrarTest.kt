package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.action.GameActionDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PlayerPortraitSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.player.PublicPlayerIndicatorDisplayRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.preparation.RoundPreparationDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.GameConfigPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.room.RoomMemberAppearanceSourceRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.ExhaustiveDrawReasonDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.MatchSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.settlement.WinSettlementPresentationTemplateRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.showcase.WinCelebrationShowcaseRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.sound.GameActionSoundPresentationRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.table.RoundInfoLineDisplayRegistryImpl
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
    ): MinecraftMahjongExtensionRegistrationResult = MinecraftMahjongExtensionRegistrar.registerAndFreeze(
        extensions = extensions,
        registries = MinecraftPresentationRegistries(
            tileAssetRegistry = tileAssetRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
            gameActionDisplayNameRegistry = GameActionDisplayNameRegistryImpl(),
            gameActionSoundPresentationRegistry = GameActionSoundPresentationRegistryImpl(),
            exhaustiveDrawReasonDisplayNameRegistry = ExhaustiveDrawReasonDisplayNameRegistryImpl(),
            roundPreparationDisplayNameRegistry = RoundPreparationDisplayNameRegistryImpl(),
            roundInfoLineDisplayRegistry = RoundInfoLineDisplayRegistryImpl(),
            winCelebrationShowcaseRegistry = WinCelebrationShowcaseRegistryImpl(),
            winSettlementTemplateRegistry = WinSettlementPresentationTemplateRegistryImpl(),
            matchSettlementTemplateRegistry = MatchSettlementPresentationTemplateRegistryImpl(),
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            playerPortraitSourceRegistry = PlayerPortraitSourceRegistryImpl(),
            publicPlayerIndicatorDisplayRegistry = PublicPlayerIndicatorDisplayRegistryImpl(),
            roomMemberAppearanceSourceRegistry = RoomMemberAppearanceSourceRegistryImpl(),
            gameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(),
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
        }

        val result = registerAndFreeze(
            extensions = listOf(extension),
            tileAssetRegistry = tileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
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
    }

    /** 驗證未實作 [MinecraftMahjongExtension] 的清單一樣能完成內建映射與凍結。 */
    @Test
    fun `no third-party extensions still registers built-ins and freezes`() {
        val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
        val aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl()
        val tileDisplayNameRegistry = TileDisplayNameRegistryImpl()
        val ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl()
        val tileEmojiRegistry = TileEmojiRegistryImpl()
        val tileLabelRegistry = TileLabelRegistryImpl()

        registerAndFreeze(
            extensions = emptyList(),
            tileAssetRegistry = tileAssetRegistry,
            aiStrategyDisplayNameRegistry = aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry = tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry = ruleModuleDisplayNameRegistry,
            tileEmojiRegistry = tileEmojiRegistry,
            tileLabelRegistry = tileLabelRegistry,
        )

        assertEquals("flower_spring", tileAssetRegistry.find(TaiwanTileTypes.SPRING))
        assertTrue(tileAssetRegistry.isFrozen)
        assertNull(tileAssetRegistry.find(TileTypeId.parse("example:unregistered")))

        assertTrue(aiStrategyDisplayNameRegistry.find(RandomAiStrategy.KEY) != null)
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

/** 取得測試指定診斷分類的 registration key。 */
private fun MinecraftMahjongExtensionRegistrationResult.registrationKeys(categoryId: String): Set<String> = categories
    .single { it.id == categoryId }
    .registrationKeys
