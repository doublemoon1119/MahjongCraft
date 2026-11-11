package com.doublemoon1119.mahjongcraft.flow.server.game.usecase

import com.doublemoon1119.mahjongcraft.extension.MahjongExtension
import com.doublemoon1119.mahjongcraft.flow.network.dto.api.registration.NetworkDtoRegistrars
import com.doublemoon1119.mahjongcraft.flow.persistence.format.api.registration.PersistenceRegistrars
import com.doublemoon1119.mahjongcraft.flow.server.game.orchestration.RuleContractViolationException
import com.doublemoon1119.mahjongcraft.logic.api.registration.ComponentRegistrar
import com.doublemoon1119.mahjongcraft.logic.api.registration.MahjongModuleRegistrar
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentLifecycleDefinition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentPatch
import com.doublemoon1119.mahjongcraft.logic.component.ComponentRoundTransition
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiFamilyRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiPlayerComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTableComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.ippatsuEndPatch
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.riichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.bundledCoreExtensionRegistries
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiComponentRegistryForTests
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiDeclarationComponents
import com.doublemoon1119.mahjongcraft.testing.logic.rules.riichi.riichiTableComponents
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import kotlin.uuid.Uuid

/** 驗證規則掛勾回傳的玩家組件變更在當下套用並檢查舊值、對象、玩家與適用規則。 */
class RuleComponentCheckerTest {
    /** 受測的日麻玩家。 */
    private val player = FakeMahjongPlayerFactory.create()

    /** 另一位玩家。 */
    private val other = FakeMahjongPlayerFactory.create()

    /** 日麻桌況。 */
    private val table = FakeTableStateFactory.create(
        players = listOf(player, other),
        config = RiichiRuleConfig(),
        components = riichiTableComponents(potPoints = 0),
    )

    /** 使用日麻登記的檢查器。 */
    private val checker = RuleComponentChecker(riichiComponentRegistryForTests(), "mahjongcraft:riichi")

    /** 舊值相符的變更只改動該玩家的組件。 */
    @Test
    fun `matching player patch changes only that player's components`() {
        val patch = ComponentPatch.basedOn(player.components, values = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit))

        val updated = checker.applyPlayerPatches(table, table.players, mapOf(player.id to patch), "onMeldCompleted")

        assertEquals(Unit, updated.first().components[RiichiPlayerComponents.PERMANENT_FURITEN])
        assertEquals(table.players.first().copy(components = updated.first().components), updated.first())
        assertSame(table.players.last(), updated.last())
    }

    /** 舊值已過時、對象不是玩家或指向局外玩家的變更都以規則契約失敗中止。 */
    @Test
    fun `invalid player patches are rule contract violations`() {
        val stale = ComponentPatch(
            removals = setOf(RiichiPlayerComponents.PERMANENT_FURITEN),
            expected = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit),
        )
        val tableOwned = ComponentPatch(values = ComponentSet.EMPTY.with(RiichiTableComponents.POT_POINTS, 1000))

        assertFailsWith<RuleContractViolationException> { checker.applyPlayerPatch(table, player, stale, "onPlayerDrew") }
        assertFailsWith<RuleContractViolationException> { checker.applyPlayerPatch(table, player, tableOwned, "onPlayerDrew") }
        assertFailsWith<RuleContractViolationException> {
            checker.applyPlayerPatches(table, table.players, mapOf(Uuid.random() to ComponentPatch()), "onMeldCompleted")
        }
    }

    /** 同一位玩家同時帶日麻與另一個擴充的資料時，日麻掛勾的變更只改日麻資料，另一份保持原值。 */
    @Test
    fun `riichi hook patch keeps another extension's player component`() {
        val otherNote = ComponentType("test:other_note", String::class, { it }, target = BuiltInComponentOwners.PLAYER)
        val registry = bundledCoreExtensionRegistries(
            additionalExtensions = listOf(
                object : MahjongExtension {
                    override val id: String = "test:other_extension"
                    override fun registerRuleModules(registry: MahjongModuleRegistrar) = Unit
                    override fun registerNetworkDtos(registries: NetworkDtoRegistrars) = Unit
                    override fun registerPersistenceDtos(registries: PersistenceRegistrars) = Unit
                    override fun registerComponents(registry: ComponentRegistrar) {
                        registry.register(otherNote)
                        registry.registerLifecycle(
                            ComponentLifecycleDefinition(
                                component = otherNote,
                                ownerType = BuiltInComponentOwners.PLAYER,
                                appliesTo = { _, config -> config is RiichiFamilyRuleConfig },
                                required = false,
                                roundTransition = ComponentRoundTransition.RETAIN,
                                initialValue = { null },
                            ),
                        )
                    }
                },
            ),
        ).componentRegistry
        val declarer = table.players.first().copy(
            components = riichiDeclarationComponents(declarationTile = FakeIdentifiedTileFactory.create(Tile.Honor.East), isIppatsu = true)
                .with(otherNote, "kept"),
        )

        val updated = RuleComponentChecker(registry, "mahjongcraft:riichi").applyPlayerPatch(table, declarer, declarer.ippatsuEndPatch(), "onPlayerDiscarded")

        assertEquals(false, updated.riichiDeclaration?.isIppatsu)
        assertEquals("kept", updated.components[otherNote])
    }

    /** 規則資料不屬於目前規則時，變更在當下就被拒絕。 */
    @Test
    fun `player patch outside the current rule is rejected`() {
        val taiwanTable = table.copy(config = TaiwanRuleConfig())
        val patch = ComponentPatch(values = ComponentSet.EMPTY.with(RiichiPlayerComponents.PERMANENT_FURITEN, Unit))

        assertFailsWith<RuleContractViolationException> { checker.applyPlayerPatch(taiwanTable, player, patch, "onPlayerDeclinedWin") }
    }
}
