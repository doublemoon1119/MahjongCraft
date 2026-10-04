package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.riichi.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiHandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.riichiCanonical
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.yaku.YakuType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueResolution
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.ALL_TILE_ASSET_KEYS
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.registerBuiltInTileAssets
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeHandFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.FakeRiichiHandValueContextFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證日麻目錄覆蓋、配置邊界、範例牌面與既有計算器一致。 */
class RiichiCatalogueProviderTest {
    /** 內建役種與特殊說明不缺漏，也不把流局條件混成一般役。 */
    @Test
    fun `catalogue covers every built in yaku and separate special categories`() {
        assertEquals(YakuType.entries.toSet(), RiichiCatalogueYaku.entries.map { it.type }.toSet())
        val catalogue = checkNotNull(RiichiCatalogueProvider().catalogue(RiichiRuleConfig()))
        assertEquals(YakuType.entries.size + RiichiCatalogueSpecial.entries.size, catalogue.entries.size)
        assertEquals(catalogue.entries.size, catalogue.entries.map { it.id }.distinct().size)
        assertEquals(3, catalogue.entries.count { it.categoryId == RiichiCatalogueCategory.BONUS.id })
        assertEquals(4, catalogue.entries.count { it.categoryId == RiichiCatalogueCategory.ABORTIVE_DRAW.id })
        assertEquals(1, catalogue.entries.count { it.categoryId == RiichiCatalogueCategory.MANGAN.id })
        assertTrue(catalogue.entries.all { it.descriptionTranslationKey.isNotBlank() })
    }

    /** 配置只調整正確的條件；古役保留欄位不能產生不存在的役。 */
    @Test
    fun `configuration changes labels without changing catalogue identities`() {
        val provider = RiichiCatalogueProvider()
        assertNull(provider.catalogue(TaiwanRuleConfig()))
        val default = checkNotNull(provider.catalogue(RiichiRuleConfig()))
        val restricted = checkNotNull(provider.catalogue(RiichiRuleConfig(allowOpenTanyao = false, redDoraCount = 0, useLocalYaku = true)))
        assertEquals(default.categories, restricted.categories)
        assertEquals(default.entries.map { it.id }, restricted.entries.map { it.id })
        val tanyao = restricted.entries.single { it.id == RiichiCatalogueYaku.Tanyao.id }
        assertTrue(RiichiCatalogueKeys.CLOSED_ONLY in tanyao.labelTranslationKeys)
        assertNull(tanyao.unavailableReasonTranslationKey)
        assertEquals(RiichiCatalogueKeys.RED_DORA_UNAVAILABLE, restricted.entries.single { it.id == RiichiCatalogueYaku.AkaDora.id }.unavailableReasonTranslationKey)
        assertNull(default.entries.single { it.id == RiichiCatalogueYaku.AkaDora.id }.unavailableReasonTranslationKey)
        assertEquals(RiichiCatalogueKeys.DRAGON_NAME, default.entries.single { it.id == RiichiCatalogueYaku.Dragon.id }.nameTranslationKey)
    }

    /** 一般說明仍標記為預設配置，台麻不冒用日麻目錄。 */
    @Test
    fun `built in registration resolves only riichi and remains frozen`() {
        val registry = RuleCatalogueRegistryImpl()
        registry.registerBuiltInRuleCatalogues()
        registry.freeze()
        assertTrue(assertIs<RuleCatalogueResolution.Available>(registry.resolve(BuiltInRuleModuleIds.RIICHI)).usesDefaultConfig)
        assertEquals(RuleCatalogueResolution.MissingProvider, registry.resolve(BuiltInRuleModuleIds.TAIWAN))
        assertEquals(RuleCatalogueResolution.UnsupportedConfig, registry.resolve(BuiltInRuleModuleIds.RIICHI, TaiwanRuleConfig()))
        assertTrue(registry.isFrozen)
    }

    /** 內建範例沿用可解析的現有素材，不製造普通牌種 ID。 */
    @Test
    fun `every example tile resolves a built in asset`() {
        val registry = MinecraftTileAssetRegistryImpl().apply { registerBuiltInTileAssets() }
        val catalogue = checkNotNull(RiichiCatalogueProvider().catalogue(RiichiRuleConfig()))
        catalogue.entries.flatMap { it.examples }.flatMap { it.groups }.flatMap { it.tiles }.forEach { tile ->
            val key = tile.toAssetKey(registry)
            assertTrue(key in ALL_TILE_ASSET_KEYS && key != UNKNOWN_TILE_ASSET_KEY, "Example tile must resolve a known asset: $tile")
        }
    }

    /** 完整範例在明確情境下必須成立目標役，不以其他役或役滿冒充成功。 */
    @Test
    fun `every complete example produces its target yaku`() {
        RiichiCatalogueYaku.entries.forEach { definition ->
            val examples = riichiCatalogueExamples(definition.type)
            assertTrue(examples.isNotEmpty(), "Missing example for ${definition.type}")
            if (definition.category != RiichiCatalogueCategory.BONUS) assertTrue(examples.all { it.completeHand }, "Yaku example must be a complete hand: ${definition.type}")
            examples.filter { it.completeHand }.forEach { example ->
                val result = evaluateExample(definition.type, example)
                assertTrue(result.yakuResults.any { it.yaku == definition.type }, "${definition.type} example produced ${result.yakuResults}")
            }
        }
    }
}

/**
 * 將說明範例轉為測試計算情境，所有計算均限制在測試邊界。
 *
 * @param type 範例欲說明的役種。
 * @param example 完整手牌及明確分組。
 * @return 現行日麻計算器的結果。
 */
private fun evaluateExample(type: YakuType, example: RuleCatalogueExample) = run {
    val winning = example.groups.single { it.role == RuleCatalogueTileGroupRole.WINNING_TILE }.tiles.single()
    val kanCount = example.groups.count { it.role == RuleCatalogueTileGroupRole.OPEN_KAN || it.role == RuleCatalogueTileGroupRole.CLOSED_KAN }
    val allTiles = example.groups.flatMap { it.tiles }
    assertEquals(14 + kanCount, allTiles.size, "Invalid physical tile count for $type")
    assertTrue(allTiles.groupingBy { it.riichiCanonical }.eachCount().values.all { it <= 4 }, "Too many copies in $type example")
    val meldGroups = example.groups.filter { it.role in meldRoles }
    val melds = meldGroups.map(::exampleMeld)
    val isMenzen = meldGroups.none { it.role != RuleCatalogueTileGroupRole.CLOSED_KAN }
    val hand = FakeHandFactory.create(example.groups.filter { it.role == RuleCatalogueTileGroupRole.HAND }.flatMap { it.tiles }, melds)
    val context = FakeRiichiHandValueContextFactory.create(
        hand = hand,
        winningTile = winning,
        isTsumo = type in tsumoTypes,
        isMenzen = isMenzen,
        isDealer = type == YakuType.Tenhou,
        seatWind = when (type) {
            YakuType.SeatWind -> Wind.WEST
            YakuType.Tenhou -> Wind.EAST
            else -> Wind.SOUTH
        },
        roundWind = if (type == YakuType.RoundWind) Wind.WEST else Wind.EAST,
        isRiichi = type in riichiTypes,
        isDoubleRiichi = type == YakuType.DoubleRiichi,
        isIppatsu = type == YakuType.Ippatsu,
        isLastDraw = type == YakuType.Haitei,
        isLastDiscard = type == YakuType.Houtei,
        isRobbingKan = type == YakuType.Chankan,
        isRinshanKaihou = type == YakuType.RinshanKaihou,
        isFirstTurn = type == YakuType.Tenhou || type == YakuType.Chiihou,
    )
    RiichiHandValueCalculator().calculate(context)
}

/** 範例中需轉成副露的牌組角色。 */
private val meldRoles = setOf(RuleCatalogueTileGroupRole.OPEN_MELD, RuleCatalogueTileGroupRole.OPEN_KAN, RuleCatalogueTileGroupRole.CLOSED_KAN)

/** 使用自摸情境驗證的役種。 */
private val tsumoTypes = setOf(YakuType.Menzentsumo, YakuType.RinshanKaihou, YakuType.Haitei, YakuType.Tenhou, YakuType.Chiihou, YakuType.Suuankou)

/** 使用已立直情境驗證的役種。 */
private val riichiTypes = setOf(YakuType.Riichi, YakuType.DoubleRiichi, YakuType.Ippatsu)

/**
 * 將已明確標記的範例副露轉成測試模型。
 *
 * @param group 副露牌組。
 * @return 測試使用的副露。
 */
private fun exampleMeld(group: RuleCatalogueTileGroup): Meld {
    val type = when (group.role) {
        RuleCatalogueTileGroupRole.OPEN_KAN -> MeldType.OPEN_KAN
        RuleCatalogueTileGroupRole.CLOSED_KAN -> MeldType.CLOSED_KAN
        else -> if (group.tiles.distinct().size == 1) MeldType.PON else MeldType.CHI
    }
    val tiles = group.tiles.map { FakeIdentifiedTileFactory.create(it) }
    return Meld(type, tiles, if (type == MeldType.CLOSED_KAN) null else tiles.last(), if (type == MeldType.CLOSED_KAN) RelativeDirection.Self else RelativeDirection.Left)
}
