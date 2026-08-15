package com.doublemoon1119.mahjongcraft.platform.minecraft.extension

import com.doublemoon1119.mahjongcraft.ai.RandomAiStrategy
import com.doublemoon1119.mahjongcraft.logic.base.TileTypeId
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileTypes
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tile.TaiwanTileTypes
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.ai.AiStrategyDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.rule.RuleModuleDisplayNameRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistry
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.TileDisplayNameRegistryImpl
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證第三方 Minecraft extension 的統一註冊順序、錯誤診斷與 registry freeze。 */
class MinecraftMahjongExtensionRegistrarTest {

    /** 驗證內建映射先完成註冊，第三方映射接續登記，完成後禁止新增映射。 */
    @Test
    fun `extension registers tile assets and ai strategy names after built-in mappings and before freeze`() {
        val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
        val aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl()
        val tileDisplayNameRegistry = TileDisplayNameRegistryImpl()
        val ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl()
        val thirdPartyId = TileTypeId.parse("example:animal/cat")
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
        }

        val result = MinecraftMahjongExtensionRegistrar.registerAndFreeze(
            listOf(extension),
            tileAssetRegistry,
            aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry,
        )

        assertEquals("m5_red", tileAssetRegistry.find(RiichiTileTypes.RED_FIVE_CHARACTER))
        assertEquals("animal_cat", tileAssetRegistry.find(thirdPartyId))
        assertEquals(listOf("animal_cat"), result.thirdPartyTileAssetKeys)
        assertTrue(tileAssetRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileAssetRegistry.register(TileTypeId.parse("example:late"), "late_key")
        }

        assertEquals(
            "example.ai_strategy.aggressive",
            aiStrategyDisplayNameRegistry.find("example:aggressive"),
        )
        assertEquals(listOf("example:aggressive"), result.thirdPartyAiStrategyKeys)
        assertTrue(aiStrategyDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            aiStrategyDisplayNameRegistry.register("example:late", "example.ai_strategy.late")
        }

        assertTrue(tileDisplayNameRegistry.find(RiichiTileTypes.RED_FIVE_CHARACTER) != null)
        assertEquals("example.tile.cat", tileDisplayNameRegistry.find(thirdPartyId))
        assertEquals(listOf(thirdPartyId.toString()), result.thirdPartyTileDisplayNameKeys)
        assertTrue(tileDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            tileDisplayNameRegistry.register(TileTypeId.parse("example:late"), "example.tile.late")
        }

        assertTrue(ruleModuleDisplayNameRegistry.find("mahjongcraft:riichi") != null)
        assertEquals(
            "example.rule_module.my_rule",
            ruleModuleDisplayNameRegistry.find("example:my_rule"),
        )
        assertEquals(listOf("example:my_rule"), result.thirdPartyRuleModuleDisplayNameKeys)
        assertTrue(ruleModuleDisplayNameRegistry.isFrozen)
        assertFailsWith<IllegalStateException> {
            ruleModuleDisplayNameRegistry.register("example:late", "example.rule_module.late")
        }
    }

    /** 驗證未實作 [MinecraftMahjongExtension] 的清單一樣能完成內建映射與凍結。 */
    @Test
    fun `no third-party extensions still registers built-ins and freezes`() {
        val tileAssetRegistry = MinecraftTileAssetRegistryImpl()
        val aiStrategyDisplayNameRegistry = AiStrategyDisplayNameRegistryImpl()
        val tileDisplayNameRegistry = TileDisplayNameRegistryImpl()
        val ruleModuleDisplayNameRegistry = RuleModuleDisplayNameRegistryImpl()

        MinecraftMahjongExtensionRegistrar.registerAndFreeze(
            emptyList(),
            tileAssetRegistry,
            aiStrategyDisplayNameRegistry,
            tileDisplayNameRegistry,
            ruleModuleDisplayNameRegistry,
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
            MinecraftMahjongExtensionRegistrar.registerAndFreeze(
                listOf(extension),
                MinecraftTileAssetRegistryImpl(),
                AiStrategyDisplayNameRegistryImpl(),
                TileDisplayNameRegistryImpl(),
                RuleModuleDisplayNameRegistryImpl(),
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
            MinecraftMahjongExtensionRegistrar.registerAndFreeze(
                listOf(extension, extension),
                MinecraftTileAssetRegistryImpl(),
                AiStrategyDisplayNameRegistryImpl(),
                TileDisplayNameRegistryImpl(),
                RuleModuleDisplayNameRegistryImpl(),
            )
        }

        assertTrue(error.message.orEmpty().contains(extension.id))
        assertTrue(error.cause?.message.orEmpty().contains("Duplicate"))
    }
}
