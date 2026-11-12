package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanHandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanHandValueContext
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanWinSituation
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.tai.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.table.Wind
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueExample
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroup
import com.doublemoon1119.mahjongcraft.platform.minecraft.catalogue.RuleCatalogueTileGroupRole
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BuiltInMinecraftMahjongExtension
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.UNKNOWN_TILE_ASSET_KEY
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.allTileAssetKeys
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.toAssetKey
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證台灣麻將圖鑑完整涵蓋台表與依房間設定標示不可用項目。 */
class TaiwanCatalogueProviderTest {
    /** 每個計台項目恰有一筆條目，並列出所有流局。 */
    @Test
    fun `catalogue covers every tai type and draw reason`() {
        val catalogue = assertNotNull(TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig()))
        val drawEntries = catalogue.entries.filter { it.categoryId == TaiwanCatalogueCategory.Draw.id }

        assertEquals(TaiwanCatalogueTai.entries.map { it.id }.toSet(), catalogue.entries.map { it.id }.filter { id -> drawEntries.none { it.id == id } }.toSet())
        assertEquals(TaiwanCatalogueSpecial.entries.map { it.id }, drawEntries.map { it.id })
    }

    /** 圖鑑對照表中每個計台項目恰好出現一次，條目識別碼不重複。 */
    @Test
    fun `catalogue table maps each tai type once`() {
        val catalogue = assertNotNull(TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig()))

        assertEquals(TaiwanTaiType.entries, TaiwanCatalogueTai.entries.map { it.type })
        assertEquals(TaiwanCatalogueTai.entries.size + TaiwanCatalogueSpecial.entries.size, catalogue.entries.size)
        assertEquals(catalogue.entries.size, catalogue.entries.map { it.id }.distinct().size)
    }

    /** 停用花牌與四風連打只標示相關條目不可用，不從一般說明中刪除。 */
    @Test
    fun `disabled options keep entries with an unavailable reason`() {
        val catalogue = assertNotNull(
            TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig(useFlowerTiles = false, fourWindsAbortiveDraw = false)),
        )

        listOf(TaiwanCatalogueTai.SeatFlower.id, TaiwanCatalogueTai.FlowerSet.id, TaiwanCatalogueTai.EightFlowers.id, TaiwanCatalogueTai.SevenFlowers.id).forEach { id ->
            assertEquals(TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE, catalogue.entries.single { it.id == id }.unavailableReasonTranslationKey)
        }
        assertEquals(
            TaiwanCatalogueKeys.FOUR_WINDS_UNAVAILABLE,
            catalogue.entries.single { it.id == TaiwanCatalogueSpecial.FourWinds.id }.unavailableReasonTranslationKey,
        )
        assertNull(catalogue.entries.single { it.id == TaiwanCatalogueTai.ConcealedHand.id }.unavailableReasonTranslationKey)
    }

    /** 每個計台項目都有牌面範例；一般牌型與情境使用完整十六張手牌加一張和牌。 */
    @Test
    fun `every tai entry provides a suitable example`() {
        val catalogue = assertNotNull(TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig()))
        val illustrationTypes = setOf(
            TaiwanTaiType.SeatFlower,
            TaiwanTaiType.FlowerSet,
            TaiwanTaiType.EightFlowers,
            TaiwanTaiType.SevenFlowers,
        )

        TaiwanTaiType.entries.forEach { type ->
            val example = catalogue.entries.single { it.id == type.catalogueId }.examples.single()

            if (type in illustrationTypes) {
                assertEquals(false, example.completeHand, type.name)
            } else {
                assertTrue(example.completeHand, type.name)
                assertEquals(17, example.groups.sumOf { it.tiles.size }, type.name)
            }
        }
    }

    /** 計台項目的主要分類直接反映台數，流局保留獨立分類。 */
    @Test
    fun `tai entries are grouped by value`() {
        val catalogue = assertNotNull(TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig()))

        TaiwanTaiType.entries.forEach { type ->
            assertEquals(
                "mahjongcraft:catalogue/taiwan/category/tai_${EXPECTED_TAI.getValue(type)}",
                catalogue.entries.single { it.id == type.catalogueId }.categoryId,
                type.name,
            )
        }
        assertTrue(
            TaiwanCatalogueSpecial.entries.all { definition ->
                catalogue.entries.single { it.id == definition.id }.categoryId == TaiwanCatalogueCategory.Draw.id
            },
        )
    }

    /** 內建範例沿用可解析的現有素材，不以未知牌面掩蓋錯誤。 */
    @Test
    fun `every example tile resolves a built in asset`() {
        val registry = MinecraftTileAssetRegistryImpl().apply {
            BuiltInMinecraftMahjongExtension.registerTileAssets(this)
            BundledMinecraftMahjongExtensions.all.forEach { it.registerTileAssets(this) }
            freeze()
        }
        val catalogue = assertNotNull(TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig()))

        catalogue.entries.flatMap { it.examples }.flatMap { it.groups }.flatMap { it.tiles }.forEach { tile ->
            val key = tile.toAssetKey(registry)
            assertTrue(key in registry.allTileAssetKeys() && key != UNKNOWN_TILE_ASSET_KEY, "Example tile must resolve a known asset: $tile")
        }
    }

    /** 每個完整牌例都必須由現行台麻計台器判定成立目標台種，不能只湊足十七張牌。 */
    @Test
    fun `every complete example produces its target tai type`() {
        TaiwanTaiType.entries.forEach { type ->
            taiwanCatalogueExamples(type).filter { it.completeHand }.forEach { example ->
                val result = evaluateExample(type, example)
                assertTrue(result.taiResults.any { it.type == type }, "$type example produced ${result.taiResults}")
            }
        }
    }
}

/**
 * 將完整圖鑑牌例轉成現行台麻計台器的輸入。
 *
 * @param type 牌例要說明的台種。
 * @param example 完整手牌及明確分組。
 * @return 現行台麻計台器的結果。
 */
private fun evaluateExample(type: TaiwanTaiType, example: RuleCatalogueExample) = run {
    val winning = example.groups.single { it.role == RuleCatalogueTileGroupRole.WINNING_TILE }.tiles.single().toValue()
    val kanCount = example.groups.count { it.role == RuleCatalogueTileGroupRole.OPEN_KAN || it.role == RuleCatalogueTileGroupRole.CLOSED_KAN }
    val allTiles = example.groups.flatMap { it.tiles }
    assertEquals(17 + kanCount, allTiles.size, "Invalid physical tile count for $type")
    assertTrue(allTiles.groupingBy { it.tile }.eachCount().values.all { it <= 4 }, "Too many copies in $type example")
    val meldGroups = example.groups.filter { it.role in meldRoles }
    val hand = Hand(
        tiles = example.groups
            .filter { it.role == RuleCatalogueTileGroupRole.HAND }
            .flatMap { it.tiles }
            .map { FakeIdentifiedTileFactory.create(it.toValue()) },
        melds = meldGroups.map(::exampleMeld),
    )
    val context = TaiwanHandValueContext(
        hand = hand,
        winningTileValue = winning,
        isTsumo = type in tsumoTypes,
        roundWind = if (type == TaiwanTaiType.RoundWind) Wind.SOUTH else Wind.EAST,
        seatWind = if (type == TaiwanTaiType.SeatWind || type == TaiwanTaiType.HeavenlyWin) Wind.EAST else Wind.SOUTH,
        flowers = emptyList(),
        situation = TaiwanWinSituation(
            isRobbingKong = type == TaiwanTaiType.RobbingKong,
            isReplacementWin = type == TaiwanTaiType.ReplacementWin,
            isLastDraw = type == TaiwanTaiType.LastDraw,
            openingWin = type.takeIf { it in openingWinTypes },
            readyQualification = type.takeIf { it in readyTypes },
        ),
    )
    TaiwanHandValueCalculator(TaiwanRuleConfig()).calculate(context)
}

/** 將不帶特殊外觀的圖鑑牌面轉成計台用牌值。 */
private fun TileFaceView.toValue(): TileValue = TileValue(tile)

/** 範例中需轉成副露的牌組角色。 */
private val meldRoles = setOf(RuleCatalogueTileGroupRole.OPEN_MELD, RuleCatalogueTileGroupRole.OPEN_KAN, RuleCatalogueTileGroupRole.CLOSED_KAN)

/** 使用自摸情境驗證的台種。 */
private val tsumoTypes = setOf(
    TaiwanTaiType.SelfDraw,
    TaiwanTaiType.FullyConcealedHand,
    TaiwanTaiType.ReplacementWin,
    TaiwanTaiType.LastDraw,
    TaiwanTaiType.EarthlyWin,
    TaiwanTaiType.HeavenlyWin,
)

/** 需要透過開局胡牌情境成立的台種。 */
private val openingWinTypes = setOf(TaiwanTaiType.HumanWin, TaiwanTaiType.EarthlyWin, TaiwanTaiType.HeavenlyWin)

/** 需要透過宣告聽牌資格成立的台種。 */
private val readyTypes = setOf(TaiwanTaiType.DeclaredReady, TaiwanTaiType.HeavenlyReady, TaiwanTaiType.EarthlyReady)

/**
 * 將牌例中明確標示的副露轉成計台模型。
 *
 * @param group 副露牌組。
 * @return 測試使用的副露。
 */
private fun exampleMeld(group: RuleCatalogueTileGroup): Meld {
    val type = when (group.role) {
        RuleCatalogueTileGroupRole.OPEN_KAN -> MeldType.OpenKan
        RuleCatalogueTileGroupRole.CLOSED_KAN -> MeldType.ClosedKan
        else -> if (group.tiles.map { it.tile }.distinct().size == 1) MeldType.Pon else MeldType.Chi
    }
    val tiles = group.tiles.map { FakeIdentifiedTileFactory.create(it.toValue()) }
    return Meld(
        type = type,
        tiles = tiles,
        sourceTile = if (type == MeldType.ClosedKan) null else tiles.last(),
        sourceDirection = if (type == MeldType.ClosedKan) RelativeDirection.Self else RelativeDirection.Left,
        publiclyRevealedTileIds = tiles.mapTo(LinkedHashSet()) { it.id },
        faceDownTileIds = emptySet(),
    )
}

/** 預設台表中每個計台項目的台數，用來核對圖鑑分類。 */
private val EXPECTED_TAI: Map<TaiwanTaiType, Int> = mapOf(
    TaiwanTaiType.ConcealedHand to 1,
    TaiwanTaiType.SelfDraw to 1,
    TaiwanTaiType.FullyConcealedHand to 1,
    TaiwanTaiType.SeatWind to 1,
    TaiwanTaiType.RoundWind to 1,
    TaiwanTaiType.Dragon to 1,
    TaiwanTaiType.SeatFlower to 1,
    TaiwanTaiType.FlowerSet to 1,
    TaiwanTaiType.SingleWait to 1,
    TaiwanTaiType.RobbingKong to 1,
    TaiwanTaiType.ReplacementWin to 1,
    TaiwanTaiType.LastDraw to 1,
    TaiwanTaiType.AllSequences to 2,
    TaiwanTaiType.ThreeConcealedTriplets to 2,
    TaiwanTaiType.FourConcealedTriplets to 5,
    TaiwanTaiType.FiveConcealedTriplets to 8,
    TaiwanTaiType.AllExposed to 2,
    TaiwanTaiType.AllTriplets to 4,
    TaiwanTaiType.LittleDragons to 4,
    TaiwanTaiType.BigDragons to 8,
    TaiwanTaiType.HalfFlush to 4,
    TaiwanTaiType.FullFlush to 8,
    TaiwanTaiType.LittleWinds to 8,
    TaiwanTaiType.BigWinds to 16,
    TaiwanTaiType.AllHonors to 16,
    TaiwanTaiType.DeclaredReady to 1,
    TaiwanTaiType.HeavenlyReady to 16,
    TaiwanTaiType.EarthlyReady to 8,
    TaiwanTaiType.HumanWin to 16,
    TaiwanTaiType.EarthlyWin to 16,
    TaiwanTaiType.HeavenlyWin to 24,
    TaiwanTaiType.EightFlowers to 8,
    TaiwanTaiType.SevenFlowers to 8,
)

/** 計台項目在圖鑑對照表中的條目識別碼。 */
private val TaiwanTaiType.catalogueId: String
    get() = TaiwanCatalogueTai.entries.single { it.type == this }.id
