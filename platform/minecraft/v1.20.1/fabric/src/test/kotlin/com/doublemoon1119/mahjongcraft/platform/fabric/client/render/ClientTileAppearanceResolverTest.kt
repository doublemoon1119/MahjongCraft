package com.doublemoon1119.mahjongcraft.platform.fabric.client.render

import com.doublemoon1119.mahjongcraft.flow.network.dto.rule.DefaultNetworkDtoRegistries
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.config.MahjongRuleConfig
import com.doublemoon1119.mahjongcraft.logic.module.BuiltInRuleModuleIds
import com.doublemoon1119.mahjongcraft.logic.module.MahjongModuleRegistryImpl
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiRuleConfig
import com.doublemoon1119.mahjongcraft.logic.rules.taiwan.TaiwanRuleConfig
import com.doublemoon1119.mahjongcraft.logic.table.TableStateSnapshot
import com.doublemoon1119.mahjongcraft.logic.table.toSnapshot
import com.doublemoon1119.mahjongcraft.platform.fabric.client.state.ClientMahjongStateStore
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftRuleTileAppearanceDefinition
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileAssetRegistryImpl
import com.doublemoon1119.mahjongcraft.platform.minecraft.tile.MinecraftTileSetAppearanceDefinition
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledNetworkDtos
import com.doublemoon1119.mahjongcraft.testing.flow.bundled.registerBundledRuleModules
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeMahjongPlayerFactory
import com.doublemoon1119.mahjongcraft.testing.logic.table.FakeTableStateFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.uuid.Uuid

/** 驗證 client 牌組外觀只依公開規則解析，並在桌況與 session 生命週期變動時正確更新。 */
class ClientTileAppearanceResolverTest {

    /** 驗證兩張桌子的牌組外觀快取互不覆蓋。 */
    @Test
    fun `appearance cache is isolated per table`() {
        val (resolver, store) = fixture()
        val firstTable = Uuid.random()
        val secondTable = Uuid.random()

        store.applyGameSnapshot(firstTable, snapshot(firstTable, RiichiRuleConfig()), emptySet())
        store.applyGameSnapshot(secondTable, snapshot(secondTable, TaiwanRuleConfig()), emptySet())

        assertEquals(RIICHI_APPEARANCE, resolver.resolve(firstTable).id)
        assertEquals(TAIWAN_APPEARANCE, resolver.resolve(secondTable).id)
        assertEquals(RIICHI_APPEARANCE, resolver.resolve(firstTable).id)
    }

    /** 驗證 snapshot 清除後仍可讓正在展示的舊桌面使用原本的牌組外觀。 */
    @Test
    fun `snapshot clearing keeps appearance for an active presentation`() {
        val (resolver, store) = fixture()
        val tableId = Uuid.random()
        store.applyGameSnapshot(tableId, snapshot(tableId, RiichiRuleConfig()), emptySet())
        assertEquals(RIICHI_APPEARANCE, resolver.resolve(tableId).id)

        store.applySnapshotCleared(tableId)

        assertEquals(RIICHI_APPEARANCE, resolver.resolve(tableId).id)
    }

    /** 驗證同一桌換成另一套規則後，新的公開快照會覆蓋舊牌組外觀。 */
    @Test
    fun `a new game on the same table replaces the cached appearance`() {
        val (resolver, store) = fixture()
        val tableId = Uuid.random()
        store.applyGameSnapshot(tableId, snapshot(tableId, RiichiRuleConfig()), emptySet())
        assertEquals(RIICHI_APPEARANCE, resolver.resolve(tableId).id)

        store.applyGameSnapshot(tableId, snapshot(tableId, TaiwanRuleConfig()), emptySet())

        assertEquals(TAIWAN_APPEARANCE, resolver.resolve(tableId).id)
    }

    /** 驗證 session 清理會丟棄舊桌快取，避免新世界沿用舊牌組外觀。 */
    @Test
    fun `session clear removes cached appearance`() {
        val (resolver, store) = fixture()
        val tableId = Uuid.random()
        store.applyGameSnapshot(tableId, snapshot(tableId, RiichiRuleConfig()), emptySet())
        assertEquals(RIICHI_APPEARANCE, resolver.resolve(tableId).id)

        resolver.clear()
        store.applySnapshotCleared(tableId)

        assertEquals(DEFAULT_APPEARANCE, resolver.resolve(tableId).id)
    }

    /** 驗證解析器只看公開規則設定，不會因為公開或隱藏牌種不同而改變牌組外觀。 */
    @Test
    fun `hidden tile identity cannot influence appearance selection`() {
        val (resolver, store) = fixture()
        val tableId = Uuid.random()
        val publicSnapshot = snapshot(tableId, RiichiRuleConfig())

        store.applyGameSnapshot(tableId, publicSnapshot, emptySet())
        val first = resolver.resolve(tableId)
        store.applyGameSnapshot(
            tableId,
            publicSnapshot.copy(revealedHandTiles = listOf(IdentifiedTile(Uuid.random(), Tile.Honor.North))),
            emptySet(),
        )

        assertEquals(first, resolver.resolve(tableId))
        assertEquals(RIICHI_APPEARANCE, first.id)
    }

    /** 建立不依賴 Minecraft client 的 resolver 測試 fixture。 */
    private fun fixture(): Pair<ClientTileAppearanceResolver, ClientMahjongStateStore> {
        val networkRegistries = DefaultNetworkDtoRegistries().apply { registerBundledNetworkDtos() }
        val store = ClientMahjongStateStore(networkRegistries)
        val modules = MahjongModuleRegistryImpl().apply { registerBundledRuleModules() }
        val assets = MinecraftTileAssetRegistryImpl().apply {
            register(riichiAppearance())
            register(taiwanAppearance())
            register(MinecraftRuleTileAppearanceDefinition(BuiltInRuleModuleIds.RIICHI, RIICHI_APPEARANCE))
            register(MinecraftRuleTileAppearanceDefinition(BuiltInRuleModuleIds.TAIWAN, TAIWAN_APPEARANCE))
        }
        return ClientTileAppearanceResolver(store, modules, assets) to store
    }

    /** 建立指定規則的公開桌況快照。 */
    private fun snapshot(
        tableId: Uuid,
        config: MahjongRuleConfig,
    ): TableStateSnapshot = when (config) {
        is RiichiRuleConfig -> FakeTableStateFactory.create(
            id = tableId,
            players = listOf(FakeMahjongPlayerFactory.create()),
            config = config,
        ).toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { emptyList() })
        is TaiwanRuleConfig -> FakeTableStateFactory.create(
            id = tableId,
            players = listOf(FakeMahjongPlayerFactory.create()),
            config = config,
        ).toSnapshot(visibleHandPlayerIds = emptySet(), setAsideTiles = { emptyList() })
        else -> error("Unsupported test config")
    }

    /** 建立測試用四人日麻牌組外觀。 */
    private fun riichiAppearance() = MinecraftTileSetAppearanceDefinition(
        id = RIICHI_APPEARANCE,
        coverTextureId = "example:tile/cover",
        unknownFaceTextureId = "example:tile/hidden",
    )

    /** 建立測試用台灣麻將牌組外觀。 */
    private fun taiwanAppearance() = MinecraftTileSetAppearanceDefinition(
        id = TAIWAN_APPEARANCE,
        coverTextureId = "example:tile/cover_taiwan",
        unknownFaceTextureId = "example:tile/hidden_taiwan",
    )

    private companion object {
        const val RIICHI_APPEARANCE = "example:riichi_tiles"
        const val TAIWAN_APPEARANCE = "example:taiwan_tiles"
        const val DEFAULT_APPEARANCE = "mahjongcraft:default"
    }
}
