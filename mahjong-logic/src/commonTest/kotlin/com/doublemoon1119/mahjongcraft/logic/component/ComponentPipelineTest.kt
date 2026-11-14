package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 檢查多來源效果、授權輸出及登記邊界，避免回到單一規則或整包同步。 */
class ComponentPipelineTest {
    /** 單次測試共享的權威輸入。 */
    private val table = FakeTableStateFactory.create()

    /** 測試用第一種具型別值。 */
    private val first = ComponentType("test:first", Int::class, { it }, target = BuiltInComponentOwners.TABLE)

    /** 測試用第二種具型別值。 */
    private val second = ComponentType("test:second", String::class, { it }, target = BuiltInComponentOwners.TABLE)

    /** 不同來源的資料並存，反轉登記順序也有相同結果。 */
    @Test
    fun `independent effects survive reversed registration order`() {
        val a = ComponentEffectDefinition(
            id = "test:effect_a",
            ownerType = BuiltInComponentOwners.TABLE,
            appliesTo = { id, _ -> id == "test:rule" },
            resolve = { ComponentSet.EMPTY.with(first, 3) },
        )
        val b = ComponentEffectDefinition(
            id = "test:effect_b",
            ownerType = BuiltInComponentOwners.TABLE,
            appliesTo = { id, _ -> id == "test:rule" },
            resolve = { ComponentSet.EMPTY.with(second, "value") },
        )
        val forward = registry().apply {
            registerEffect(a)
            registerEffect(b)
        }
        val reverse = registry().apply {
            registerEffect(b)
            registerEffect(a)
        }
        val context = effectContext()
        val result = forward.resolveEffects(context)
        assertEquals(result, reverse.resolveEffects(context))
        assertEquals(3, result[first])
        assertEquals("value", result[second])
        assertTrue(forward.resolveEffects(context.copy(ruleModuleId = "test:other")).isEmpty)
    }

    /** 衝突不以最後一個來源為準，也不套用高層覆寫規則。 */
    @Test
    fun `conflicting peer effects fail instead of overriding`() {
        val registry = registry()
        registry.registerEffect(effect("test:a", 1))
        registry.registerEffect(effect("test:b", 2))
        assertFailsWith<IllegalArgumentException> { registry.resolveEffects(effectContext()) }
    }

    /** 明訂同層合併的值也不能使用依來源順序不同的函式。 */
    @Test
    fun `peer merge rejects source dependent results`() {
        val type = ComponentType(
            id = "test:order_dependent",
            valueClass = Int::class,
            copyValue = { it },
            target = BuiltInComponentOwners.TABLE,
            mergePeerValues = { left, _ -> left },
        )
        assertFailsWith<IllegalArgumentException> { type.mergePeers(1, 2) }
    }

    /** 私有值只對本人產生輸出，公共讀取者連 key 都收不到。 */
    @Test
    fun `private projection does not disclose raw key or value to public readers`() {
        val registry = registry()
        val view = ComponentViewType("test:private_counter", Int::class, { it })
        val permittedPlayer = table.players.first().id
        registry.registerView(view)
        val projection = ComponentProjectionDefinition(
            id = "test:counter_projection",
            ownerType = BuiltInComponentOwners.TABLE,
            component = first,
            viewType = view,
            appliesTo = { _, _ -> true },
            project = { context, value ->
                value.takeIf { context.reader == ComponentReadContext.Player(permittedPlayer) }
            },
        )
        registry.registerProjection(projection)
        val components = ComponentSet.EMPTY.with(first, 3000).with(second, "not projected")
        val context = ComponentProjectionContext(
            ruleModuleId = "test:rule",
            tableState = table,
            ownerType = BuiltInComponentOwners.TABLE,
            owner = table,
            reader = ComponentReadContext.Public,
        )
        assertTrue(registry.project(context, components).isEmpty)
        assertTrue(registry.project(context.copy(reader = ComponentReadContext.Spectator(Uuid.random())), components).isEmpty)
        val own = registry.project(context.copy(reader = ComponentReadContext.Player(permittedPlayer)), components)
        assertEquals(3000, own[view])
        assertEquals(listOf(view), own.types)
        registry.freeze()
        assertFailsWith<IllegalStateException> { registry.registerProjection(projection) }
        assertFailsWith<IllegalStateException> { registry.registerEffect(effect("test:late", 1)) }
    }

    /** 對外值可以與權威值有不同型別，不能拿同 ID 的新定義冒充。 */
    @Test
    fun `projection changes value type and rejects impersonated views`() {
        val registry = registry()
        val view = ComponentViewType("test:label", String::class, { it })
        registry.registerView(view)
        registry.registerProjection(
            definition = ComponentProjectionDefinition(
                id = "test:label_projection",
                ownerType = BuiltInComponentOwners.TABLE,
                component = first,
                viewType = view,
                appliesTo = { _, _ -> true },
                project = { _, count -> "$count points" },
            ),
        )
        val result = registry.project(
            context = ComponentProjectionContext("test:rule", table, BuiltInComponentOwners.TABLE, table, ComponentReadContext.Public),
            components = ComponentSet.EMPTY.with(first, 3000),
        )
        assertEquals("3000 points", result[view])
        val impostor = ComponentViewType(view.id, String::class, { it })
        assertFailsWith<IllegalArgumentException> { result[impostor] }
    }

    /** 建立只登記本測試所需型別的空 registry。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        register(first)
        register(second)
    }

    /** 建立同一版本的衍生效果情境。 */
    private fun effectContext(): ComponentEffectContext<TableState> = ComponentEffectContext(
        ruleModuleId = "test:rule",
        tableState = table,
        ownerType = BuiltInComponentOwners.TABLE,
        owner = table,
    )

    /**
     * 建立回傳單一值的效果來源。
     *
     * @param id 來源識別碼。
     * @param value 回傳值。
     * @return 本測試用的效果定義。
     */
    private fun effect(id: String, value: Int): ComponentEffectDefinition<TableState> = ComponentEffectDefinition(
        id = id,
        ownerType = BuiltInComponentOwners.TABLE,
        appliesTo = { _, _ -> true },
        resolve = { ComponentSet.EMPTY.with(first, value) },
    )
}
