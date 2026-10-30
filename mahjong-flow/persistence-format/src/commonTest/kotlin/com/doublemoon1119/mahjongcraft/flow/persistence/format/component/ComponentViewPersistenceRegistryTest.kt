package com.doublemoon1119.mahjongcraft.flow.persistence.format.component

import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.UnregisteredPersistenceTypeException
import com.doublemoon1119.mahjongcraft.flow.persistence.format.core.persistenceJson
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.TileFaceViewPersistenceDto
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toDomain
import com.doublemoon1119.mahjongcraft.flow.persistence.format.game.toPersistenceDto
import com.doublemoon1119.mahjongcraft.logic.base.Tile
import com.doublemoon1119.mahjongcraft.logic.base.TileFaceView
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewSet
import com.doublemoon1119.mahjongcraft.logic.component.ComponentViewType
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.RiichiTableComponentViews
import com.doublemoon1119.mahjongcraft.logic.rules.riichi.tile.RiichiTileComponentViews
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

/** 已裁切投影的保存驗證，與權威組件的解碼入口分開。 */
class ComponentViewPersistenceRegistryTest {
    /** 牌面與公開特徵可獨立保存，不需要局內 UUID、權威牌張或規則回呼。 */
    @Test
    fun `known face features round trip without tile identity`() {
        val registry = registry()
        val face = TileFaceView(
            tile = Tile.Numeric(Tile.Suit.Dot, 5),
            views = ComponentViewSet.EMPTY.with(RiichiTileComponentViews.RED_DORA, Unit),
            appearanceId = "test:appearance",
        )

        val saved = persistenceJson.encodeToString(TileFaceViewPersistenceDto.serializer(), face.toPersistenceDto(registry))
        val restored = persistenceJson.decodeFromString(TileFaceViewPersistenceDto.serializer(), saved).toDomain(registry)

        assertEquals(face, restored)
        assertFalse(saved.contains("tileId"))
        assertEquals(Unit, restored.views[RiichiTileComponentViews.RED_DORA])
    }

    /** 同一份已裁切資料可包含不同型別的投影，還原後保留原有型別與順序規格。 */
    @Test
    fun `typed projected values retain their definitions`() {
        val registry = registry()
        val views = ComponentViewSet.EMPTY
            .with(RiichiTableComponentViews.POT_POINTS, 2000)
            .with(RiichiTileComponentViews.RED_DORA, Unit)

        assertEquals(views, registry.decode(registry.encode(views)))
    }

    /** 未知與重複投影都拒絕，不能悄悄丟棄原本的公開資料。 */
    @Test
    fun `unknown duplicate and mismatched definitions are rejected`() {
        val registry = registry()
        val views = ComponentViewSet.EMPTY.with(RiichiTileComponentViews.RED_DORA, Unit)
        val saved = registry.encode(views)

        assertFailsWith<UnregisteredPersistenceTypeException> { ComponentViewPersistenceRegistry().decode(saved) }
        assertFailsWith<IllegalArgumentException> { registry.decode(saved + saved) }
        val mismatched = ComponentViewType(
            id = RiichiTileComponentViews.RED_DORA.id,
            valueClass = Unit::class,
            copyValue = {},
        )
        assertFailsWith<IllegalArgumentException> { registry.encode(ComponentViewSet.EMPTY.with(mismatched, Unit)) }
        assertFailsWith<IllegalArgumentException> { registry.decode(saved.map { it.copy(typeKey = RiichiTableComponentViews.POT_POINTS.id) }) }
    }

    /** 凍結後不可新增，同一身分也不可在凍結前重複登記。 */
    @Test
    fun `registration rejects duplicates and changes after freezing`() {
        val mutable = ComponentViewPersistenceRegistry()
        mutable.register(redDefinition())
        assertFailsWith<IllegalArgumentException> { mutable.register(redDefinition()) }
        mutable.freeze()
        assertFailsWith<IllegalStateException> { mutable.register(redDefinition()) }
    }

    /** 建立供牌面及整數投影保存使用的登記。 */
    private fun registry(): ComponentViewPersistenceRegistry = ComponentViewPersistenceRegistry().apply {
        register(redDefinition())
        register(
            ComponentViewPersistenceDefinition(
                viewType = RiichiTableComponentViews.POT_POINTS,
                serializer = ComponentIntPersistenceDto.serializer(),
                toDto = ::ComponentIntPersistenceDto,
                toDomain = { it.value },
            ),
        )
        freeze()
    }

    /** 建立赤寶牌已公開特徵的保存轉換。 */
    private fun redDefinition(): ComponentViewPersistenceDefinition<Unit, ComponentUnitPersistenceDto> = ComponentViewPersistenceDefinition(
        viewType = RiichiTileComponentViews.RED_DORA,
        serializer = ComponentUnitPersistenceDto.serializer(),
        toDto = { ComponentUnitPersistenceDto },
        toDomain = {},
    )
}
