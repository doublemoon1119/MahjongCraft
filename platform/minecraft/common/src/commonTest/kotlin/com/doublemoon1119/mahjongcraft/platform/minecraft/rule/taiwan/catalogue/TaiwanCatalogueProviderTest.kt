package com.doublemoon1119.mahjongcraft.platform.minecraft.rule.taiwan.catalogue

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.Meld
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.RelativeDirection
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.base.TileValue
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanExhaustiveDrawReason
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanHandValueCalculator
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanHandValueContext
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanTaiType
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanWinSituation
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
    /** 每個計台項目恰有一筆條目，並列出四種流局。 */
    @Test
    fun `catalogue covers every tai type and draw reason`() {
        val catalogue = assertNotNull(TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig()))
        val expectedDrawIds = setOf(
            TaiwanExhaustiveDrawReason.Normal.id,
            TaiwanExhaustiveDrawReason.NoSupplementalTile.id,
            TaiwanExhaustiveDrawReason.FourKans.id,
            TaiwanExhaustiveDrawReason.FourWinds.id,
        )

        assertEquals(TaiwanTaiType.entries.map { it.id }.toSet(), catalogue.entries.map { it.id }.filter { it !in expectedDrawIds }.toSet())
        assertTrue(expectedDrawIds.all { id -> catalogue.entries.any { it.id == id } })
    }

    /** 停用花牌與四風連打只標示相關條目不可用，不從一般說明中刪除。 */
    @Test
    fun `disabled options keep entries with an unavailable reason`() {
        val catalogue = assertNotNull(
            TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig(useFlowerTiles = false, fourWindsAbortiveDraw = false)),
        )

        listOf(TaiwanTaiType.SEAT_FLOWER.id, TaiwanTaiType.FLOWER_SET.id, TaiwanTaiType.EIGHT_FLOWERS.id, TaiwanTaiType.SEVEN_FLOWERS.id).forEach { id ->
            assertEquals(TaiwanCatalogueKeys.FLOWERS_UNAVAILABLE, catalogue.entries.single { it.id == id }.unavailableReasonTranslationKey)
        }
        assertEquals(
            TaiwanCatalogueKeys.FOUR_WINDS_UNAVAILABLE,
            catalogue.entries.single { it.id == TaiwanExhaustiveDrawReason.FourWinds.id }.unavailableReasonTranslationKey,
        )
        assertNull(catalogue.entries.single { it.id == TaiwanTaiType.CONCEALED_HAND.id }.unavailableReasonTranslationKey)
    }

    /** 每個計台項目都有牌面範例；一般牌型與情境使用完整十六張手牌加一張和牌。 */
    @Test
    fun `every tai entry provides a suitable example`() {
        val catalogue = assertNotNull(TaiwanCatalogueProvider().catalogue(TaiwanRuleConfig()))
        val illustrationTypes = setOf(
            TaiwanTaiType.SEAT_FLOWER,
            TaiwanTaiType.FLOWER_SET,
            TaiwanTaiType.EIGHT_FLOWERS,
            TaiwanTaiType.SEVEN_FLOWERS,
        )

        TaiwanTaiType.entries.forEach { type ->
            val example = catalogue.entries.single { it.id == type.id }.examples.single()

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
                "mahjongcraft:catalogue/taiwan/category/tai_${type.tai}",
                catalogue.entries.single { it.id == type.id }.categoryId,
                type.name,
            )
        }
        assertTrue(
            catalogue.entries
                .filter { it.id.startsWith("mahjongcraft:taiwan/exhaustive_draw/") }
                .all { it.categoryId == TaiwanCatalogueCategory.DRAW.id },
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
        roundWind = if (type == TaiwanTaiType.ROUND_WIND) Wind.SOUTH else Wind.EAST,
        seatWind = if (type == TaiwanTaiType.SEAT_WIND || type == TaiwanTaiType.HEAVENLY_WIN) Wind.EAST else Wind.SOUTH,
        flowers = emptyList(),
        situation = TaiwanWinSituation(
            isRobbingKong = type == TaiwanTaiType.ROBBING_KONG,
            isReplacementWin = type == TaiwanTaiType.REPLACEMENT_WIN,
            isLastDraw = type == TaiwanTaiType.LAST_DRAW,
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
    TaiwanTaiType.SELF_DRAW,
    TaiwanTaiType.FULLY_CONCEALED_HAND,
    TaiwanTaiType.REPLACEMENT_WIN,
    TaiwanTaiType.LAST_DRAW,
    TaiwanTaiType.EARTHLY_WIN,
    TaiwanTaiType.HEAVENLY_WIN,
)

/** 需要透過開局胡牌情境成立的台種。 */
private val openingWinTypes = setOf(TaiwanTaiType.HUMAN_WIN, TaiwanTaiType.EARTHLY_WIN, TaiwanTaiType.HEAVENLY_WIN)

/** 需要透過宣告聽牌資格成立的台種。 */
private val readyTypes = setOf(TaiwanTaiType.DECLARED_READY, TaiwanTaiType.HEAVENLY_READY, TaiwanTaiType.EARTHLY_READY)

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
