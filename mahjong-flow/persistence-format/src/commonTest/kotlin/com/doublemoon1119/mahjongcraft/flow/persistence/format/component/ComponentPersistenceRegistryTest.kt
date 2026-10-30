package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.TypedPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.rule.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.IdentifiedTile
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.component.BuiltInComponentOwners
import com.doublemoon1119.mahjongcraft.logic.component.ComponentSet
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardComponents
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiDiscardPile
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.isRiichiDeclaration
import com.doublemoon1119.mahjongcraft.logic.table.DiscardPile.DiscardEntry
import com.doublemoon1119.mahjongcraft.logic.tile.component.BuiltInTileComponents
import com.doublemoon1119.mahjongcraft.logic.tile.component.TileFaceAccess
import com.doublemoon1119.mahjongcraft.logic.tile.component.TilePose
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import kotlin.uuid.Uuid

/** 驗證組件保存不吞掉未知資料，也不混淆牌張與捨牌紀錄。 */
class ComponentPersistenceRegistryTest {
    /** 多種已登記組件經過實際 JSON 編解碼後仍保留原值。 */
    @Test
    fun `registered components survive serialized round trip`() {
        val registry = registry()
        val playerId = Uuid.random()
        val original = ComponentSet.EMPTY
            .with(BuiltInTileComponents.FACE_ACCESS, TileFaceAccess.Players(setOf(playerId)))
            .with(BuiltInTileComponents.POSE, TilePose.FACE_DOWN)
            .with(BuiltInTileComponents.APPEARANCE, "example:readable")
        val encoded = registry.encode(original)
        val restored = encoded.map { dto -> persistenceJson.decodeFromString(TypedPersistenceDto.serializer(), persistenceJson.encodeToString(TypedPersistenceDto.serializer(), dto)) }
        assertEquals(original, registry.decode(restored))
        assertEquals(encoded.map { it.typeKey }.sorted(), encoded.map { it.typeKey })
    }

    /** 未知或重複的組件不能被忽略，錯誤資料也不能還原為空組件。 */
    @Test
    fun `unknown duplicate and invalid component values are rejected`() {
        val registry = registry()
        val saved = registry.encode(ComponentSet.EMPTY.with(BuiltInTileComponents.POSE, TilePose.FACE_UP)).single()
        assertFailsWith<UnregisteredPersistenceTypeException> { registry.decode(listOf(saved.copy(typeKey = "missing:component"))) }
        assertFailsWith<IllegalArgumentException> { registry.decode(listOf(saved, saved)) }
        val invalid = persistenceJson.decodeFromString(TypedPersistenceDto.serializer(), """{"typeKey":"mahjongcraft:tile/face_access","payload":{"kind":"PUBLIC","playerIds":["${Uuid.random()}"]}}""")
        assertFailsWith<IllegalArgumentException> { registry.decode(listOf(invalid)) }
    }

    /** 被鳴走的立直牌仍保留紀錄標記，但牌張本身不取得該組件。 */
    @Test
    fun `taken riichi declaration remains a record component after persistence`() {
        val registry = registry()
        val tile = IdentifiedTile(Uuid.random(), Tile.Numeric(Tile.Suit.Character, 1))
        val original = RiichiDiscardPile().discard(DiscardEntry(tile, components = RiichiDiscardComponents.declaration())).takeLast()
        val restored = original.toPersistenceDto(registry).toDomain(registry)
        val entry = restored.entries.single()
        assertTrue(entry.isTaken)
        assertTrue(entry.isRiichiDeclaration)
        assertEquals(original.entries.single().publicMarkerIds, entry.publicMarkerIds)
        assertTrue(entry.tile.components.isEmpty)
        assertEquals(BuiltInComponentOwners.DISCARD_ENTRY, RiichiDiscardComponents.RIICHI_DECLARED.target)
        assertFailsWith<IllegalArgumentException> { tile.copy(components = entry.components) }
        assertFalse(DiscardEntry(tile).isRiichiDeclaration)
    }

    /** 凍結與重複登記都會拒絕修改。 */
    @Test
    fun `persistence definitions cannot be replaced or registered after freeze`() {
        val registry = registry()
        assertFailsWith<IllegalArgumentException> { registerBuiltInComponentPersistence(registry) }
        registry.freeze()
        assertFailsWith<IllegalStateException> { registerBuiltInComponentPersistence(registry) }
    }

    /** 建立和正式擴充相同的組件保存登記。 */
    private fun registry(): ComponentPersistenceRegistry = ComponentPersistenceRegistry().apply {
        registerBuiltInComponentPersistence(this)
        register(
            ComponentPersistenceDefinition(
                component = RiichiDiscardComponents.RIICHI_DECLARED,
                serializer = ComponentUnitPersistenceDto.serializer(),
                toDto = { ComponentUnitPersistenceDto },
                toDomain = {},
            ),
        )
    }
}
