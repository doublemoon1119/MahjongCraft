package com.doublemoon1119.mahjongcraft.logic.component

import com.doublemoon1119.mahjongcraft.logic.base.Hand
import com.doublemoon1119.mahjongcraft.logic.base.MeldType
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.table.MahjongPlayer
import com.doublemoon1119.mahjongcraft.logic.table.TableState
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeIdentifiedTileFactory
import com.doublemoon1119.mahjongcraft.testing.logic.base.FakeMeldFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeDiscardPile
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

/** 驗證動作新建的捨牌紀錄與副露套用初值，且帶著不屬於目前規則的資料時被拒絕。 */
class RecordComponentInitializationTest {
    /** 新捨牌紀錄的標記，有初值。 */
    private val discardNote = ComponentType("test:discard_note", String::class, { it }, target = BuiltInComponentOwners.DISCARD_ENTRY)

    /** 只適用其他規則的捨牌紀錄資料。 */
    private val otherRuleDiscard = ComponentType("test:other_rule_discard", Boolean::class, { it }, target = BuiltInComponentOwners.DISCARD_ENTRY)

    /** 沒有登記生命週期的捨牌紀錄資料。 */
    private val unmanagedDiscard = ComponentType("test:unmanaged_discard", Boolean::class, { it }, target = BuiltInComponentOwners.DISCARD_ENTRY)

    /** 新副露的標記，有初值。 */
    private val meldNote = ComponentType("test:meld_note", String::class, { it }, target = BuiltInComponentOwners.MELD)

    /** 新捨牌紀錄帶上初值；動作前已存在的紀錄不重新初始化。 */
    @Test
    fun `new discard record receives initial values`() {
        val before = table(FakeMahjongPlayerFactory.create(discardPile = FakeDiscardPile().discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.East))))
        val after = before.withPlayer { it.copy(discardPile = it.discardPile.discardTile(FakeIdentifiedTileFactory.create(Tile.Honor.South))) }

        val initialized = after.initializeCreatedRecords(before, RULE_ID, registry())

        val entries = initialized.players.single().discardPile.entries
        assertNull(entries.first().components[discardNote])
        assertEquals(INITIAL_NOTE, entries.last().components[discardNote])
    }

    /** 建立者寫入的值優先於初值。 */
    @Test
    fun `value written by the creator takes precedence`() {
        val before = table(FakeMahjongPlayerFactory.create(discardPile = FakeDiscardPile()))
        val written = FakeDiscardPile.FakeEntry(FakeIdentifiedTileFactory.create(Tile.Honor.West), components = ComponentSet.EMPTY.with(discardNote, "written"))
        val after = before.withPlayer { it.copy(discardPile = (it.discardPile as FakeDiscardPile).discard(written)) }

        val initialized = after.initializeCreatedRecords(before, RULE_ID, registry())

        assertEquals("written", initialized.players.single().discardPile.entries.single().components[discardNote])
    }

    /** 新副露帶上初值；加槓沿用同一個副露 ID 時保留原組件，不重新初始化。 */
    @Test
    fun `new meld receives initial values while an upgraded meld keeps its components`() {
        val ponTiles = List(3) { FakeIdentifiedTileFactory.create(Tile.Honor.Red) }
        val pon = FakeMeldFactory.create(MeldType.PON, ponTiles).copy(components = ComponentSet.EMPTY.with(meldNote, "kept"))
        val fourth = FakeIdentifiedTileFactory.create(Tile.Honor.Red)
        val before = table(FakeMahjongPlayerFactory.create(hand = Hand(tiles = listOf(fourth), melds = listOf(pon))))
        val newMeld = FakeMeldFactory.create(MeldType.CHI, List(3) { FakeIdentifiedTileFactory.create(Tile.Numeric(Tile.Suit.Dot, it + 1)) })
        val after = before.withPlayer { player ->
            val upgraded = player.hand.upgradeToAddedKan(fourth, 0)
            player.copy(hand = upgraded.copy(melds = upgraded.melds + newMeld))
        }

        val melds = after.initializeCreatedRecords(before, RULE_ID, registry()).players.single().hand.melds

        assertEquals(pon.id, melds.first().id)
        assertEquals("kept", melds.first().components[meldNote])
        assertEquals(INITIAL_NOTE, melds.last().components[meldNote])
    }

    /** 新紀錄帶著不屬於目前規則或沒有登記生命週期的資料時拒絕。 */
    @Test
    fun `new record with a component outside the current rule is rejected`() {
        val before = table(FakeMahjongPlayerFactory.create(discardPile = FakeDiscardPile()))
        listOf(otherRuleDiscard, unmanagedDiscard).forEach { type ->
            val entry = FakeDiscardPile.FakeEntry(FakeIdentifiedTileFactory.create(Tile.Honor.North), components = ComponentSet.EMPTY.with(type, true))
            val after = before.withPlayer { it.copy(discardPile = (it.discardPile as FakeDiscardPile).discard(entry)) }

            assertFailsWith<IllegalArgumentException> { after.initializeCreatedRecords(before, RULE_ID, registry()) }
        }
    }

    /** 沒有新紀錄時回傳同一份玩家資料。 */
    @Test
    fun `table without new records is unchanged`() {
        val before = table(FakeMahjongPlayerFactory.create(discardPile = FakeDiscardPile()))

        assertSame(before.players.single(), before.initializeCreatedRecords(before, RULE_ID, registry()).players.single())
    }

    /**
     * 建立含初值與適用規則的測試登記。
     *
     * @return 已凍結的登記。
     */
    private fun registry(): ComponentRegistry = ComponentRegistryImpl().apply {
        BuiltInComponentOwners.ALL.forEach(::registerOwner)
        listOf(discardNote, otherRuleDiscard, unmanagedDiscard, meldNote).forEach(::register)
        registerLifecycle(lifecycle(discardNote, BuiltInComponentOwners.DISCARD_ENTRY, RULE_ID, INITIAL_NOTE))
        registerLifecycle(lifecycle(otherRuleDiscard, BuiltInComponentOwners.DISCARD_ENTRY, "test:other_rule", null))
        registerLifecycle(lifecycle(meldNote, BuiltInComponentOwners.MELD, RULE_ID, INITIAL_NOTE))
        freeze()
    }

    /**
     * 建立只適用指定規則的選用生命週期。
     *
     * @param O 對象型別。
     * @param V 值型別。
     * @param component 組件定義。
     * @param ownerType 對象定義。
     * @param ruleModuleId 適用的規則。
     * @param initialValue 新對象的初值。
     * @return 每局重建的生命週期。
     */
    private fun <O : Any, V : Any> lifecycle(
        component: ComponentType<V>,
        ownerType: ComponentOwnerType<O>,
        ruleModuleId: String,
        initialValue: V?,
    ): ComponentLifecycleDefinition<O, V> = ComponentLifecycleDefinition(
        component = component,
        ownerType = ownerType,
        appliesTo = { id, _ -> id == ruleModuleId },
        required = false,
        roundTransition = ComponentRoundTransition.RESET,
        initialValue = { initialValue },
    )

    /**
     * 建立只有一位玩家的桌況。
     *
     * @param player 唯一玩家。
     * @return 測試桌況。
     */
    private fun table(player: MahjongPlayer): TableState = FakeTableStateFactory.create(players = listOf(player))

    /**
     * 改動唯一玩家。
     *
     * @param transform 玩家變更。
     * @return 改動後的桌況。
     */
    private fun TableState.withPlayer(transform: (MahjongPlayer) -> MahjongPlayer): TableState = copy(players = players.map(transform))

    /** 測試常數。 */
    private companion object {
        /** 受測規則識別碼。 */
        const val RULE_ID = "test:rule"

        /** 新紀錄的初值。 */
        const val INITIAL_NOTE = "initial"
    }
}
