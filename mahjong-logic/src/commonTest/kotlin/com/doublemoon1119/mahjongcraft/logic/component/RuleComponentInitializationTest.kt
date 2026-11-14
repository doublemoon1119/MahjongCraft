package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.table.GameInitializationResult
import com.doublemoon1119.mahjongcraft.logic.table.TileWall
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

/** 驗證正式開局結果在同一候選桌況上建立與轉換各來源組件。 */
class RuleComponentInitializationTest {
    /** 可以跨局保留的測試資料。 */
    private val retained = ComponentType(
        id = "test:retained",
        valueClass = Int::class,
        copyValue = { it },
        target = BuiltInComponentOwners.TABLE,
    )

    /** 每局重建的另一擴充資料。 */
    private val reset = ComponentType(
        id = "another:round_data",
        valueClass = Int::class,
        copyValue = { it },
        target = BuiltInComponentOwners.TABLE,
    )

    /** 玩家每局重設的資料。 */
    private val playerData = ComponentType(
        id = "test:player_data",
        valueClass = Int::class,
        copyValue = { it },
        target = BuiltInComponentOwners.PLAYER,
    )

    /** 兩個來源同時建立，並保留牌組指定的逐張特性與原本牌張身分。 */
    @Test
    fun `initialization collects independent sources and preserves intrinsic tile components`() {
        val registry = registry()
        val original = candidate()
        val wall = original.tableState.tileWall.getAllTiles()
        val public = wall.first().copy(components = ComponentSet.EMPTY.with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Public))
        val input = original.copy(tableState = original.tableState.copy(tileWall = TileWall(listOf(public) + wall.drop(1))))
        val initialized = input.initializeRuleComponents("test:rule", registry).tableState
        assertEquals(1, initialized.components[retained])
        assertEquals(2, initialized.components[reset])
        assertEquals(3, initialized.players.single().components[playerData])
        assertEquals(public, initialized.tileWall.getAllTiles().first())
        assertEquals(wall.map { it.id }, initialized.tileWall.getAllTiles().map { it.id })
        assertEquals(original.tableState.players.map { it.id }, initialized.players.map { it.id })
        assertEquals(ComponentSet.EMPTY, original.tableState.components)
    }

    /** 換局保留與重設分開，不依玩家列表順序猜上一局玩家。 */
    @Test
    fun `round transition retains only explicitly retained values`() {
        val registry = registry()
        val initial = candidate().initializeRuleComponents("test:rule", registry).tableState
        val previous = initial.copy(
            components = initial.components.with(retained, 9).with(reset, 8),
            players = initial.players.map { it.copy(components = it.components.with(playerData, 7)) },
        )
        val result = GameInitializationResult(previous, null, null).initializeRuleComponents(
            ruleModuleId = "test:rule",
            registry = registry,
            previousTableState = previous,
        ).tableState
        assertEquals(9, result.components[retained])
        assertEquals(2, result.components[reset])
        assertEquals(3, result.players.single().components[playerData])
        assertEquals(8, previous.components[reset])
    }

    /** 必要資料遺失時不能假裝第一次初始化，也不能補成初值。 */
    @Test
    fun `round transition rejects missing required previous components`() {
        val registry = registry()
        val previous = candidate().initializeRuleComponents("test:rule", registry).tableState
        val missing = previous.copy(components = previous.components.without(retained))
        assertFailsWith<IllegalArgumentException> {
            GameInitializationResult(previous, null, null).initializeRuleComponents("test:rule", registry, missing)
        }
    }

    /** 建立不含先前組件的新局候選桌況。 */
    private fun candidate(): GameInitializationResult = GameInitializationResult(
        tableState = FakeTableStateFactory.create(players = listOf(FakeMahjongPlayerFactory.create(hand = Hand()))),
        wallStructure = null,
        initialPhysicalWallLayout = null,
    )

    /** 建立由兩個來源提供的完整建立規格。 */
    private fun registry(): ComponentRegistryImpl = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        register(BuiltInTileComponents.FACE_ACCESS)
        register(retained)
        register(reset)
        register(playerData)
        registerLifecycle(
            definition = ComponentLifecycleDefinition(
                component = retained,
                ownerType = BuiltInComponentOwners.TABLE,
                appliesTo = { _, _ -> true },
                required = true,
                roundTransition = ComponentRoundTransition.RETAIN,
                initialValue = { 1 },
            ),
        )
        registerLifecycle(
            definition = ComponentLifecycleDefinition(
                component = reset,
                ownerType = BuiltInComponentOwners.TABLE,
                appliesTo = { _, _ -> true },
                required = true,
                roundTransition = ComponentRoundTransition.RESET,
                initialValue = { 2 },
            ),
        )
        registerLifecycle(
            definition = ComponentLifecycleDefinition(
                component = playerData,
                ownerType = BuiltInComponentOwners.PLAYER,
                appliesTo = { _, _ -> true },
                required = true,
                roundTransition = ComponentRoundTransition.RESET,
                initialValue = { 3 },
            ),
        )
    }
}
