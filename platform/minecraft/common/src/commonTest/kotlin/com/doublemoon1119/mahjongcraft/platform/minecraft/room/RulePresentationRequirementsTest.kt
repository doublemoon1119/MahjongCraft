package com.doublemoon1119.mahjongcraft.platform.minecraft.room

import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RoundPreparationResolverRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleDefinition
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistry
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.module.MahjongRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleModule
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.threeplayer.ThreePlayerRiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.layout.TileWallLayout
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningContext
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningProcedure
import com.doublemoon1119.mahjongcraft.logic.table.opening.RoundOpeningStep
import com.doublemoon1119.mahjongcraft.platform.minecraft.extension.BundledMinecraftMahjongExtensions
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** 驗證規則能否在麻將桌上呈現的條件、啟動時的可選規則檢查與房間設定的可用判斷。 */
class RulePresentationRequirementsTest {
    /** 內建四人日麻、三人日麻與台麻都提供牌山布局與可擺放的骰子數量；內建可選規則通過啟動檢查。 */
    @Test
    fun `built in rules can be presented on the table`() {
        val modules = bundledModules()

        listOf(RiichiRuleConfig(), ThreePlayerRiichiRuleConfig(), TaiwanRuleConfig()).forEach { config ->
            assertNull(RulePresentationRequirements.unmetRequirement(modules.getModule(config)), "$config")
        }
        RulePresentationRequirements.verifySelectableRules(bundledPresentations(), modules)
    }

    /** 缺少牌山布局或開局程序的規則無法呈現，原因帶出規則 ID。 */
    @Test
    fun `rules without a wall cannot be presented`() {
        val noLayout = RulePresentationRequirements.unmetRequirement(adjustedModule(providesLayout = false))
        val noOpening = RulePresentationRequirements.unmetRequirement(adjustedModule(diceCount = null))

        assertTrue(assertNotNull(noLayout).contains("wall layout") && noLayout.contains(TEST_RULE_ID))
        assertTrue(assertNotNull(noOpening).contains("round opening procedure") && noOpening.contains(TEST_RULE_ID))
    }

    /** 開門一次擲出的骰子必須是桌上能擺放的數量。 */
    @Test
    fun `opening dice must be placeable on the table`() {
        listOf(1, 4).forEach { count ->
            assertNotNull(RulePresentationRequirements.unmetRequirement(adjustedModule(diceCount = count)), "$count dice")
        }
        listOf(2, 3).forEach { count ->
            assertNull(RulePresentationRequirements.unmetRequirement(adjustedModule(diceCount = count)), "$count dice")
        }
    }

    /** 不擲骰的開局程序可以用空的能力集合表示，仍可使用固定牌山布局。 */
    @Test
    fun `empty opening procedure capabilities are supported`() {
        assertNull(RulePresentationRequirements.unmetRequirement(adjustedModule(emptyOpeningProcedure = true)))
    }

    /** 啟動檢查只拒絕無法呈現的可選規則；不可選的規則不檢查。 */
    @Test
    fun `startup check rejects only selectable rules that cannot be presented`() {
        val modules = singleModuleRegistry { adjustedModule(providesLayout = false) }

        val failure = assertFailsWith<IllegalStateException> {
            RulePresentationRequirements.verifySelectableRules(presentations(definition(TEST_RULE_ID, selectable = true)), modules)
        }
        assertTrue(failure.message.orEmpty().contains(TEST_RULE_ID))
        RulePresentationRequirements.verifySelectableRules(presentations(definition(TEST_RULE_ID, selectable = false)), modules)
    }

    /** 設定呈現的預設設定建立出另一個規則時，啟動檢查拒絕這個呈現。 */
    @Test
    fun `startup check rejects presentation whose default config creates another rule`() {
        val modules = singleModuleRegistry { adjustedModule() }

        val failure = assertFailsWith<IllegalStateException> {
            RulePresentationRequirements.verifySelectableRules(presentations(definition("test:other", selectable = true)), modules)
        }
        assertTrue(failure.message.orEmpty().contains("test:other"))
    }

    /** 房間設定的規則必須有可選的設定呈現，且規則模組能在麻將桌上呈現。 */
    @Test
    fun `room rule is playable only when selectable and presentable`() {
        val presentable = singleModuleRegistry { adjustedModule() }
        val withoutWall = singleModuleRegistry { adjustedModule(providesLayout = false) }

        assertTrue(RulePresentationRequirements.isPlayable(RiichiRuleConfig(), presentations(definition(TEST_RULE_ID, selectable = true)), presentable))
        assertFalse(RulePresentationRequirements.isPlayable(RiichiRuleConfig(), presentations(definition(TEST_RULE_ID, selectable = false)), presentable))
        assertFalse(RulePresentationRequirements.isPlayable(RiichiRuleConfig(), presentations(), presentable))
        assertFalse(RulePresentationRequirements.isPlayable(RiichiRuleConfig(), presentations(definition(TEST_RULE_ID, selectable = true)), withoutWall))
    }

    /** 內建台麻的設定呈現不可選，因此不能用於房間設定與開局。 */
    @Test
    fun `unselectable built in rule is not playable`() {
        assertFalse(RulePresentationRequirements.isPlayable(TaiwanRuleConfig(), bundledPresentations(), bundledModules()))
        assertTrue(RulePresentationRequirements.isPlayable(RiichiRuleConfig(), bundledPresentations(), bundledModules()))
    }

    /**
     * 建立已登記所有內建規則模組的 registry。
     *
     * @return 已凍結的規則模組 registry。
     */
    private fun bundledModules(): MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
        registerBundledRuleModules()
        freeze()
    }

    /**
     * 建立已登記所有內建設定呈現的 registry。
     *
     * @return 內建設定呈現 registry。
     */
    private fun bundledPresentations(): GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(
        RoundPreparationResolverRegistry(),
    ).apply {
        BundledMinecraftMahjongExtensions.all.forEach { it.registerGameConfigPresentations(this) }
    }

    /**
     * 建立只有一個以日麻設定建立的測試規則的 registry。
     *
     * @param create 建立測試規則模組。
     * @return 已凍結的規則模組 registry。
     */
    private fun singleModuleRegistry(create: () -> MahjongRuleModule<RiichiRuleConfig>): MahjongModuleRegistry = MahjongModuleRegistryImpl().apply {
        register(MahjongModuleDefinition(RiichiRuleConfig::class, TEST_RULE_ID) { _, _ -> create() })
        freeze()
    }

    /**
     * 建立包含指定設定呈現的 registry。
     *
     * @param definitions 要登記的設定呈現。
     * @return 設定呈現 registry。
     */
    private fun presentations(vararg definitions: GameConfigPresentationDefinition): GameConfigPresentationRegistry = GameConfigPresentationRegistryImpl(
        RoundPreparationResolverRegistry(),
    ).apply { definitions.forEach(::register) }

    /**
     * 建立使用日麻預設設定、沒有欄位的設定呈現。
     *
     * @param ruleModuleId 設定呈現對應的規則 ID。
     * @param selectable 是否可選。
     * @return 測試用設定呈現。
     */
    private fun definition(ruleModuleId: String, selectable: Boolean) = GameConfigPresentationDefinition(
        ruleModuleId = ruleModuleId,
        descriptionTranslationKey = "test.rule.description",
        selectable = selectable,
        unavailableReasonTranslationKey = if (selectable) null else "test.rule.unavailable",
        defaultRuleConfig = ::RiichiRuleConfig,
        categories = emptyList(),
        fields = emptyList(),
    )

    /**
     * 建立牌山布局與開門骰子數量可調整、其餘行為沿用日麻的測試規則。
     *
     * @param providesLayout 是否提供牌山布局。
     * @param diceCount 每輪開門擲出的骰子數量；null 表示不提供擲骰程序。
     * @param emptyOpeningProcedure 是否提供不需要擲骰的開局程序。
     * @return 測試規則模組。
     */
    private fun adjustedModule(
        providesLayout: Boolean = true,
        diceCount: Int? = 2,
        emptyOpeningProcedure: Boolean = false,
    ): MahjongRuleModule<RiichiRuleConfig> = AdjustedWallModule(
        delegate = RiichiRuleModule(TEST_RULE_ID, RiichiRuleConfig()),
        providesLayout = providesLayout,
        diceCount = diceCount,
        emptyOpeningProcedure = emptyOpeningProcedure,
    )

    /**
     * 只改變牌山布局與開門骰子數量的日麻測試替身。
     *
     * @property delegate 提供其餘規則行為的日麻模組。
     * @property providesLayout 是否提供牌山布局。
     * @property diceCount 每輪開門擲出的骰子數量；null 表示不提供擲骰程序。
     * @property emptyOpeningProcedure 是否提供不需要擲骰的開局程序。
     */
    private class AdjustedWallModule(
        private val delegate: RiichiRuleModule,
        private val providesLayout: Boolean,
        private val diceCount: Int?,
        private val emptyOpeningProcedure: Boolean,
    ) : MahjongRuleModule<RiichiRuleConfig> by delegate {
        override fun createWallLayout(): TileWallLayout? = if (providesLayout) delegate.createWallLayout() else null

        override fun createRoundOpeningProcedure(): RoundOpeningProcedure? = when {
            emptyOpeningProcedure -> EmptyOpeningProcedure
            diceCount != null -> FixedDiceOpeningProcedure(diceCount)
            else -> null
        }
    }

    /**
     * 只宣告骰子數量、不會被執行的開門程序。
     *
     * @param diceCount 每輪開門擲出的骰子數量。
     */
    private class FixedDiceOpeningProcedure(
        diceCount: Int,
    ) : RoundOpeningProcedure {
        override val supportedDiceCounts: Set<Int> = setOf(diceCount)

        override fun next(context: RoundOpeningContext): RoundOpeningStep = error("Requirement checks never run the opening procedure")
    }

    /** 不需要擲骰、直接完成的開局程序替身。 */
    private object EmptyOpeningProcedure : RoundOpeningProcedure {
        override val supportedDiceCounts: Set<Int> = emptySet()

        override fun next(context: RoundOpeningContext): RoundOpeningStep = error("Requirement checks never run the opening procedure")
    }

    private companion object {
        /** 測試規則 ID。 */
        const val TEST_RULE_ID = "test:rule"
    }
}
