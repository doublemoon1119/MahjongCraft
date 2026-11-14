package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

/** 驗證換局只處理各自組件，不清掉其他擴充的資料或補上遺失的必要值。 */
class ComponentLifecycleTest {
    /** 測試使用的同一份權威桌況。 */
    private val table = FakeTableStateFactory.create()

    /** 可跨局保留的測試數值。 */
    private val retained = ComponentType("test:retained", Int::class, { it }, target = BuiltInComponentOwners.TABLE)

    /** 每局重建的測試數值。 */
    private val reset = ComponentType("test:reset", Int::class, { it }, target = BuiltInComponentOwners.TABLE)

    /** 有規則換局轉換的測試數值。 */
    private val transformed = ComponentType("test:transformed", Int::class, { it }, target = BuiltInComponentOwners.TABLE)

    /** 三種換局行為可以一起存在，不相互覆寫。 */
    @Test
    fun `round transitions retain reset and transform independent values`() {
        val registry = registry()
        registry.registerLifecycle(definition(retained, ComponentRoundTransition.RETAIN, 0))
        registry.registerLifecycle(definition(reset, ComponentRoundTransition.RESET, 1))
        registry.registerLifecycle(
            definition = ComponentLifecycleDefinition(
                component = transformed,
                ownerType = BuiltInComponentOwners.TABLE,
                appliesTo = { _, _ -> true },
                required = true,
                roundTransition = ComponentRoundTransition.TRANSFORM,
                initialValue = { 3 },
                transform = { _, old -> old + 1 },
            ),
        )
        val initialized = registry.initialize(context(previous = false))
        assertEquals(0, initialized[retained])
        assertEquals(1, initialized[reset])
        assertEquals(3, initialized[transformed])
        val before = initialized.with(retained, 3000).with(reset, 9)
        val after = registry.transitionRound(context(previous = true), before)
        assertEquals(3000, after[retained])
        assertEquals(1, after[reset])
        assertEquals(4, after[transformed])
        assertEquals(9, before[reset])
    }

    /** 適用條件不能把其他規則的資料一併初始化。 */
    @Test
    fun `lifecycle applies only to its declared rule`() {
        val registry = registry()
        registry.registerLifecycle(
            definition = ComponentLifecycleDefinition(
                component = retained,
                ownerType = BuiltInComponentOwners.TABLE,
                appliesTo = { id, _ -> id == "test:rule" },
                required = true,
                roundTransition = ComponentRoundTransition.RETAIN,
                initialValue = { 0 },
            ),
        )
        assertNull(registry.initialize(context(false).copy(ruleModuleId = "test:other"))[retained])
        assertEquals(0, registry.initialize(context(false))[retained])
        assertFailsWith<IllegalArgumentException> { registry.transitionRound(context(true), ComponentSet.EMPTY) }
        assertFailsWith<IllegalArgumentException> { registry.transitionRound(context(false), ComponentSet.EMPTY) }
    }

    /** 未宣告換局方式的已存值不能被悄悄丟掉。 */
    @Test
    fun `unmanaged stored component fails instead of disappearing`() {
        val registry = registry()
        val previous = ComponentSet.EMPTY.with(retained, 3000)
        assertFailsWith<IllegalArgumentException> { registry.transitionRound(context(true), previous) }
        assertEquals(3000, previous[retained])
    }

    /** 必要初值缺少時拒絕；凍結後也不能補登記。 */
    @Test
    fun `required initialization and frozen registrations are enforced`() {
        val registry = registry()
        val invalid = ComponentLifecycleDefinition(
            component = retained,
            ownerType = BuiltInComponentOwners.TABLE,
            appliesTo = { _, _ -> true },
            required = true,
            roundTransition = ComponentRoundTransition.RETAIN,
            initialValue = { null },
        )
        registry.registerLifecycle(invalid)
        assertFailsWith<IllegalArgumentException> { registry.initialize(context(false)) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.registerLifecycle(definition(reset, ComponentRoundTransition.RESET, 0)) }
    }

    /** 保存的組件只能出現在適用的規則中；要求生命週期的對象不接受沒有登記的組件，牌張不受此限。 */
    @Test
    fun `stored components must belong to the current rule`() {
        val tileValue = ComponentType("test:tile_value", Int::class, { it }, target = BuiltInComponentOwners.TILE)
        val registry = registry().apply {
            registerOwner(BuiltInComponentOwners.TILE)
            register(tileValue)
            registerLifecycle(
                definition = ComponentLifecycleDefinition(
                    component = retained,
                    ownerType = BuiltInComponentOwners.TABLE,
                    appliesTo = { id, _ -> id == "test:rule" },
                    required = false,
                    roundTransition = ComponentRoundTransition.RETAIN,
                    initialValue = { null },
                ),
            )
        }
        val stored = ComponentSet.EMPTY.with(retained, 1)

        registry.validateState(context(false), stored)
        assertFailsWith<IllegalArgumentException> { registry.validateState(context(false).copy(ruleModuleId = "test:other"), stored) }
        assertFailsWith<IllegalArgumentException> { registry.validateState(context(false), ComponentSet.EMPTY.with(reset, 1)) }
        val tile = table.tileWall.getAllTiles().first()
        registry.validateState(
            context = ComponentLifecycleContext(
                ruleModuleId = "test:rule",
                tableState = table,
                ownerType = BuiltInComponentOwners.TILE,
                owner = tile,
                previousTableState = null,
            ),
            components = ComponentSet.EMPTY.with(tileValue, 1),
        )
    }

    /** 其他擴充追加的適用範圍讓原生命週期也用於另一套規則，包括初值與保存檢查。 */
    @Test
    fun `lifecycle scope extends a component to another rule`() {
        val registry = registry()
        registry.registerLifecycle(
            definition = ComponentLifecycleDefinition(
                component = retained,
                ownerType = BuiltInComponentOwners.TABLE,
                appliesTo = { id, _ -> id == "test:rule" },
                required = false,
                roundTransition = ComponentRoundTransition.RETAIN,
                initialValue = { 5 },
            ),
        )
        val otherRule = context(false).copy(ruleModuleId = "test:other")
        assertFailsWith<IllegalArgumentException> { registry.validateState(otherRule, ComponentSet.EMPTY.with(retained, 1)) }

        registry.registerLifecycleScope(ComponentLifecycleScope("test:scope/other", retained) { id, _ -> id == "test:other" })
        registry.freeze()

        assertEquals(5, registry.initialize(otherRule)[retained])
        registry.validateState(otherRule, ComponentSet.EMPTY.with(retained, 1))
        assertFailsWith<IllegalArgumentException> {
            registry.validateState(context(false).copy(ruleModuleId = "test:third"), ComponentSet.EMPTY.with(retained, 1))
        }
    }

    /** 追加範圍的組件必須有生命週期；同一份範圍不能重複登記。 */
    @Test
    fun `lifecycle scope requires a lifecycle and a unique id`() {
        val registry = registry()
        val scope = ComponentLifecycleScope("test:scope/reset", reset) { _, _ -> true }
        registry.registerLifecycleScope(scope)

        assertFailsWith<IllegalArgumentException> { registry.registerLifecycleScope(scope) }
        assertFailsWith<IllegalArgumentException> { registry.freeze() }
    }

    /** 建立測試所需的組件登記。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        registerOwner(BuiltInComponentOwners.TABLE)
        register(retained)
        register(reset)
        register(transformed)
    }

    /**
     * 建立正式開局或換局的情境。
     *
     * @param previous 是否提供上一局桌況。
     * @return 只用於生命週期的情境。
     */
    private fun context(previous: Boolean): ComponentLifecycleContext<TableState> = ComponentLifecycleContext(
        ruleModuleId = "test:rule",
        tableState = table,
        ownerType = BuiltInComponentOwners.TABLE,
        owner = table,
        previousTableState = table.takeIf { previous },
    )

    /**
     * 建立具有固定初值的生命週期定義。
     *
     * @param type 組件定義。
     * @param transition 換局行為。
     * @param initial 初始值。
     * @return 本測試的生命週期定義。
     */
    private fun definition(type: ComponentType<Int>, transition: ComponentRoundTransition, initial: Int): ComponentLifecycleDefinition<TableState, Int> = ComponentLifecycleDefinition(
        component = type,
        ownerType = BuiltInComponentOwners.TABLE,
        appliesTo = { _, _ -> true },
        required = true,
        roundTransition = transition,
        initialValue = { initial },
    )
}
